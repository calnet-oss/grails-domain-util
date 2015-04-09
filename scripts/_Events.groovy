// from http://stackoverflow.com/questions/27115294/how-to-implement-a-groovy-global-ast-transformation-in-a-grails-plugin
eventCompileStart = { target ->
    compileAST("grails-app/utils", "target/classes")
}

// The AST transformer class (and dependencies) need to be compiled first
// because the transformer is used while compiling anything that uses the
// annotation (like the test domain classes).
def compileAST(def srcBaseDir, def destDir) {
    // ant is groovy.util.AntBuilder 
    ant.sequential {
        path id: "grails.compile.classpath", compileClasspath
        def classpathId = "grails.compile.classpath"
        // If we changed the AST transformers, we want to start fresh, so 
        // we're forcing a full rebuild everytime we compile.
        delete dir: destDir
        mkdir dir: destDir
        groovyc(destdir: destDir,
                srcDir: srcBaseDir,
                classpathref: classpathId,
                stacktrace: "yes",
                encoding: "UTF-8")
    }
} 
