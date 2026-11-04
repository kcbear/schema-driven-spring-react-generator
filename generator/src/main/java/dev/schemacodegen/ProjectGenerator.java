package dev.schemacodegen;

import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;
import java.util.stream.Collectors;

final class ProjectGenerator {
    static void generate(ResourceModel m, Path output, boolean force) throws IOException {
        if (Files.exists(output) && !force) {
            try (var entries = Files.list(output)) {
                if (entries.findAny().isPresent()) throw new IllegalArgumentException("Output is not empty; pass --force");
            }
        }
        String pkg = m.packageName().replace('.', '/');
        Path backend = output.resolve("backend");
        Path java = backend.resolve("src/main/java/" + pkg);
        Path frontend = output.resolve("frontend/src");
        write(backend.resolve("settings.gradle"), "rootProject.name = '" + m.name().toLowerCase(Locale.ROOT) + "-api'\n");
        write(backend.resolve("build.gradle"), """
            plugins { id 'org.springframework.boot' version '3.4.3'; id 'io.spring.dependency-management' version '1.1.7'; id 'java' }
            group = 'generated'; version = '0.1.0'
            java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
            repositories { mavenCentral() }
            dependencies {
              implementation 'org.springframework.boot:spring-boot-starter-web'
              implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
              runtimeOnly 'org.postgresql:postgresql'
              testImplementation 'org.springframework.boot:spring-boot-starter-test'
            }
            """);
        write(backend.resolve("src/main/resources/application.yml"), """
            server:
              port: 8080
            spring:
              datasource:
                url: ${DB_URL:jdbc:postgresql://localhost:5432/generated}
                username: ${DB_USER:postgres}
                password: ${DB_PASSWORD:postgres}
              jpa:
                hibernate:
                  ddl-auto: update
                open-in-view: false
            """);
        write(java.resolve("Application.java"), "package " + m.packageName() + ";\nimport org.springframework.boot.SpringApplication;\nimport org.springframework.boot.autoconfigure.SpringBootApplication;\n@SpringBootApplication public class Application { public static void main(String[] args) { SpringApplication.run(Application.class,args); } }\n");
        write(java.resolve(m.name() + ".java"), entity(m));
        write(java.resolve(m.name() + "Repository.java"), repository(m));
        write(java.resolve(m.name() + "Controller.java"), controller(m));
        if (m.demoRecords() > 0) write(java.resolve("DemoDataInitializer.java"), seedInitializer(m));
        write(frontend.resolve("types.ts"), types(m));
        write(frontend.resolve("api.ts"), api(m));
        write(frontend.resolve("App.tsx"), app(m));
        write(frontend.resolve("main.tsx"), "import React from 'react'; import ReactDOM from 'react-dom/client'; import { QueryClient, QueryClientProvider } from '@tanstack/react-query'; import App from './App'; const client=new QueryClient(); ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><QueryClientProvider client={client}><App/></QueryClientProvider></React.StrictMode>);\n");
        write(output.resolve("frontend/index.html"), "<div id=\"root\"></div><script type=\"module\" src=\"/src/main.tsx\"></script>\n");
        write(output.resolve("frontend/package.json"), "{\"type\":\"module\",\"scripts\":{\"dev\":\"vite\",\"build\":\"tsc && vite build\"},\"dependencies\":{\"@tanstack/react-query\":\"^5.66.0\",\"@tanstack/react-table\":\"^8.21.2\",\"react\":\"^18.3.1\",\"react-dom\":\"^18.3.1\"},\"devDependencies\":{\"@types/react\":\"^18.3.18\",\"@types/react-dom\":\"^18.3.5\",\"@vitejs/plugin-react\":\"^4.3.4\",\"typescript\":\"^5.7.3\",\"vite\":\"^6.1.0\"}}\n");
        write(output.resolve("frontend/tsconfig.json"), """
            {"compilerOptions":{"target":"ES2022","useDefineForClassFields":true,"lib":["ES2022","DOM","DOM.Iterable"],"module":"ESNext","skipLibCheck":true,"moduleResolution":"bundler","allowImportingTsExtensions":true,"resolveJsonModule":true,"isolatedModules":true,"noEmit":true,"jsx":"react-jsx","strict":true}}
            """);
        write(output.resolve("frontend/vite.config.ts"), """
            import { defineConfig } from 'vite';
            import react from '@vitejs/plugin-react';
            export default defineConfig({plugins:[react()],server:{proxy:{'/api':'http://localhost:8080'}}});
            """);
        write(output.resolve("README.md"), "Generated CRUD starter for " + m.name() + ". Configure DB_URL, DB_USER and DB_PASSWORD, then run Gradle in backend and npm install/npm run dev in frontend.\n");
    }

    private static void write(Path p, String s) throws IOException { Files.createDirectories(p.getParent()); Files.writeString(p, s); }
    private static String cap(String s) { return Character.toUpperCase(s.charAt(0)) + s.substring(1); }
    private static String jt(String t) { return switch(t) { case "integer" -> "Long"; case "number" -> "java.math.BigDecimal"; case "boolean" -> "Boolean"; case "date" -> "java.time.LocalDate"; case "datetime" -> "java.time.OffsetDateTime"; default -> "String"; }; }
    private static String ts(String t) { return switch(t) { case "integer","number" -> "number"; case "boolean" -> "boolean"; default -> "string"; }; }

    private static String entity(ResourceModel m) {
        FieldModel id = m.fields().stream().filter(f -> f.name().equals(m.idField())).findFirst().orElseThrow();
        String declarations = m.fields().stream().filter(f -> !f.name().equals(m.idField())).map(f -> "    private " + jt(f.type()) + " " + f.name() + ";\n").collect(Collectors.joining());
        String accessors = m.fields().stream().map(f -> "    public " + jt(f.type()) + " get" + cap(f.name()) + "() { return " + f.name() + "; }\n    public void set" + cap(f.name()) + "(" + jt(f.type()) + " value) { this." + f.name() + " = value; }\n").collect(Collectors.joining());
        String generated = id.type().equals("integer") ? "@GeneratedValue(strategy = GenerationType.IDENTITY) " : "";
        return "package " + m.packageName() + ";\nimport jakarta.persistence.*;\nimport java.time.*;\n@Entity @Table(name=\"" + m.plural().replace('-', '_') + "\")\npublic class " + m.name() + " {\n    @Id " + generated + "private " + jt(id.type()) + " " + id.name() + ";\n" + declarations + accessors + "}\n";
    }
    private static String repository(ResourceModel m) {
        FieldModel id=m.fields().stream().filter(f->f.name().equals(m.idField())).findFirst().orElseThrow();
        return "package "+m.packageName()+";\nimport org.springframework.data.jpa.repository.JpaRepository;\npublic interface "+m.name()+"Repository extends JpaRepository<"+m.name()+", "+jt(id.type())+"> {}\n";
    }
    private static String controller(ResourceModel m) {
        FieldModel id=m.fields().stream().filter(f->f.name().equals(m.idField())).findFirst().orElseThrow();
        return "package "+m.packageName()+";\nimport org.springframework.data.domain.*;\nimport org.springframework.http.ResponseEntity;\nimport org.springframework.web.bind.annotation.*;\nimport java.net.URI;\n@RestController @RequestMapping(\"/api/"+m.plural()+"\") @CrossOrigin(origins=\"http://localhost:5173\")\npublic class "+m.name()+"Controller {\n private final "+m.name()+"Repository repository;\n public "+m.name()+"Controller("+m.name()+"Repository repository){this.repository=repository;}\n @GetMapping public Page<"+m.name()+"> list(@RequestParam(defaultValue=\"0\") int page,@RequestParam(defaultValue=\"50\") int size){return repository.findAll(PageRequest.of(page,Math.min(Math.max(size,1),100)));}\n @GetMapping(\"/{id}\") public ResponseEntity<"+m.name()+"> get(@PathVariable "+jt(id.type())+" id){return repository.findById(id).map(ResponseEntity::ok).orElseGet(()->ResponseEntity.notFound().build());}\n @PostMapping public ResponseEntity<"+m.name()+"> create(@RequestBody "+m.name()+" value){"+m.name()+" saved=repository.save(value);return ResponseEntity.created(URI.create(\"/api/"+m.plural()+"/\"+saved.get"+cap(id.name())+"())).body(saved);}\n @PutMapping(\"/{id}\") public ResponseEntity<"+m.name()+"> update(@PathVariable "+jt(id.type())+" id,@RequestBody "+m.name()+" value){if(!repository.existsById(id))return ResponseEntity.notFound().build();value.set"+cap(id.name())+"(id);return ResponseEntity.ok(repository.save(value));}\n @DeleteMapping(\"/{id}\") public ResponseEntity<Void> delete(@PathVariable "+jt(id.type())+" id){if(!repository.existsById(id))return ResponseEntity.notFound().build();repository.deleteById(id);return ResponseEntity.noContent().build();}\n}\n";
    }

    private static String seedInitializer(ResourceModel m) {
        String assignments = m.fields().stream()
            .filter(f -> !f.name().equals(m.idField()))
            .map(f -> "      value.set" + cap(f.name()) + "(" + seedValue(f) + ");\n")
            .collect(Collectors.joining());
        return "package " + m.packageName() + ";\n"
            + "import java.math.BigDecimal; import java.time.*; import java.util.*;\n"
            + "import org.springframework.boot.CommandLineRunner; import org.springframework.stereotype.Component;\n"
            + "@Component public class DemoDataInitializer implements CommandLineRunner {\n"
            + " private final " + m.name() + "Repository repository;\n"
            + " public DemoDataInitializer(" + m.name() + "Repository repository){this.repository=repository;}\n"
            + " @Override public void run(String... args){ if(repository.count()>0) return; "
            + "List<" + m.name() + "> rows=new ArrayList<>(" + m.demoRecords() + "); "
            + "for(int i=0;i<" + m.demoRecords() + ";i++){ " + m.name() + " value=new " + m.name() + "();\n"
            + assignments
            + " rows.add(value); } repository.saveAll(rows); }\n}\n";
    }
    private static String seedValue(FieldModel f) {
        if (f.type().equals("string")) return switch (f.name()) {
            case "symbol" -> "String.format(\"GLB%07d\", i + 1)";
            case "name" -> "new String[]{\"Aster Technologies\",\"BlueRiver Energy\",\"Cobalt Systems\",\"Delta Healthcare\",\"Evergreen Mining\",\"Frontier Bank\",\"Granite Retail\",\"Helios Motors\",\"Indigo Media\",\"Juniper Foods\",\"Keystone Telecom\",\"Lumen Robotics\",\"Meridian Pharma\",\"Northstar Logistics\",\"Orion Aerospace\",\"Pioneer Materials\",\"Quantum Software\",\"Redwood Insurance\",\"Summit Industrials\",\"Vertex Semiconductors\"}[i % 20] + \" Class \" + (i / 20 + 1)";
            case "instrumentType" -> "i % 4 == 0 ? \"ETF\" : \"STOCK\"";
            case "exchange" -> "new String[]{\"NASDAQ\",\"NYSE\",\"LSE\",\"XETRA\",\"Euronext Paris\",\"SIX\",\"HKEX\",\"Tokyo Stock Exchange\",\"ASX\",\"TSX\",\"BSE\",\"SGX\"}[i % 12]";
            case "country" -> "new String[]{\"United States\",\"United States\",\"United Kingdom\",\"Germany\",\"France\",\"Switzerland\",\"Hong Kong\",\"Japan\",\"Australia\",\"Canada\",\"India\",\"Singapore\"}[i % 12]";
            case "currency" -> "new String[]{\"USD\",\"USD\",\"GBP\",\"EUR\",\"EUR\",\"CHF\",\"HKD\",\"JPY\",\"AUD\",\"CAD\",\"INR\",\"SGD\"}[i % 12]";
            case "isin" -> "String.format(\"US%010d\", i + 1)";
            case "sector" -> "new String[]{\"Technology\",\"Financials\",\"Healthcare\",\"Energy\",\"Industrials\",\"Consumer Discretionary\",\"Consumer Staples\",\"Materials\",\"Utilities\",\"Communication Services\",\"Real Estate\"}[i % 11]";
            default -> "\"" + f.label().replace("\\", "\\\\").replace("\"", "\\\"") + " \" + (i + 1)";
        };
        return switch (f.type()) {
            case "integer" -> "Long.valueOf(10000L + (long)i * 137L)";
            case "number" -> f.name().equals("marketCap")
                ? "BigDecimal.valueOf(100000000L + ((long)i * 7919L % 900000000000L))"
                : "BigDecimal.valueOf(1000L + (i * 37L % 5000000L), 2)";
            case "boolean" -> "Boolean.valueOf(i % 17 != 0)";
            case "date" -> "LocalDate.of(2024,1,1).plusDays(i % 1000L)";
            case "datetime" -> "OffsetDateTime.of(2024,1,1,9,30,0,0,ZoneOffset.UTC).plusMinutes(i)";
            default -> "null";
        };
    }

    private static String types(ResourceModel m) {
        return "export interface "+m.name()+" {\n"+m.fields().stream().map(f ->"  "+f.name()+"?: "+ts(f.type())+";").collect(Collectors.joining("\n"))+"\n}\nexport interface Page<T>{content:T[];totalElements:number;totalPages:number;number:number;size:number}\n";
    }
    private static String api(ResourceModel m) {
        return "import type {"+m.name()+",Page} from './types';\nconst base='/api/"+m.plural()+"';\nasync function req<T>(url:string,init?:RequestInit):Promise<T>{const r=await fetch(url,{headers:{'Content-Type':'application/json'},...init});if(!r.ok)throw new Error(await r.text()||r.statusText);return r.status===204?undefined as T:r.json();}\nexport const api={list:(page=0,size=50)=>req<Page<"+m.name()+">>(base+'?page='+page+'&size='+size),create:(v:"+m.name()+")=>req<"+m.name()+">(base,{method:'POST',body:JSON.stringify(v)}),update:(id:string|number,v:"+m.name()+")=>req<"+m.name()+">(base+'/'+id,{method:'PUT',body:JSON.stringify(v)}),remove:(id:string|number)=>req<void>(base+'/'+id,{method:'DELETE'})};\n";
    }
    private static String app(ResourceModel m) {
        String columns=m.fields().stream().filter(FieldModel::list).map(f ->"h.accessor('"+f.name()+"',{header:'"+f.label().replace("'","\\'")+"'})").collect(Collectors.joining(","));
        String inputs=m.fields().stream().filter(f->f.editable()&&!f.name().equals(m.idField())).map(f->{ boolean bool=f.type().equals("boolean"); String binding=bool?"checked={Boolean(draft."+f.name()+")}" : "value={String(draft."+f.name()+"??'')}"; return "<label>"+f.label()+" <input type='"+(f.type().equals("date")?"date":bool?"checkbox":f.type().equals("number")||f.type().equals("integer")?"number":"text")+"' "+binding+" onChange={e=>setDraft(v=>({...v,"+f.name()+":"+((f.type().equals("number")||f.type().equals("integer"))?"(e.target.value===''?undefined:Number(e.target.value))":bool?"e.target.checked":"e.target.value")+"}))}/></label>"; }).collect(Collectors.joining());
        return "import {useState} from 'react';import {useQuery,useMutation,useQueryClient} from '@tanstack/react-query';import {createColumnHelper,useReactTable,getCoreRowModel,flexRender} from '@tanstack/react-table';import {api} from './api';import type {"+m.name()+"} from './types';const h=createColumnHelper<"+m.name()+">();const columns=["+columns+"];\nexport default function App(){const [page,setPage]=useState(0);const [draft,setDraft]=useState<"+m.name()+">({});const [editing,setEditing]=useState<string|number|null>(null);const qc=useQueryClient();const q=useQuery({queryKey:['"+m.plural()+"',page],queryFn:()=>api.list(page)});const save=useMutation({mutationFn:()=>editing===null?api.create(draft):api.update(editing,draft),onSuccess:()=>{qc.invalidateQueries({queryKey:['"+m.plural()+"']});setDraft({});setEditing(null);}});const del=useMutation({mutationFn:api.remove,onSuccess:()=>qc.invalidateQueries({queryKey:['"+m.plural()+"']})});const table=useReactTable({data:q.data?.content??[],columns,getCoreRowModel:getCoreRowModel()});return <main style={{maxWidth:1100,margin:'2rem auto',fontFamily:'sans-serif'}}><h1>"+m.name()+"</h1><p>Global markets · Synthetic demo data</p><h2>{editing===null?'Create':'Edit'}</h2>"+inputs+"<button onClick={()=>save.mutate()}>Save</button><button onClick={()=>{setDraft({});setEditing(null)}}>Clear</button>{q.isLoading?<p>Loading…</p>:q.isError?<p role='alert'>{String(q.error)}</p>:<table><thead>{table.getHeaderGroups().map(g=><tr key={g.id}>{g.headers.map(c=><th key={c.id}>{flexRender(c.column.columnDef.header,c.getContext())}</th>)}<th>Actions</th></tr>)}</thead><tbody>{table.getRowModel().rows.map(row=><tr key={row.id}>{row.getVisibleCells().map(c=><td key={c.id}>{String(c.getValue()??'')}</td>)}<td><button onClick={()=>{setDraft(row.original);setEditing(row.original."+m.idField()+"??null)}}>Edit</button><button onClick={()=>del.mutate(row.original."+m.idField()+" as string|number)}>Delete</button></td></tr>)}</tbody></table>}<button disabled={page===0} onClick={()=>setPage(p=>p-1)}>Previous</button><span> Showing {page*50+1}–{Math.min((page+1)*50,q.data?.totalElements??0)} of {(q.data?.totalElements??0).toLocaleString()} instruments · Page {page+1} of {q.data?.totalPages??0} </span><button disabled={!q.data||page+1>=q.data.totalPages} onClick={()=>setPage(p=>p+1)}>Next</button></main>}\n";
    }
}
