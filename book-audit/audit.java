//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
//DEPS org.springframework.boot:spring-boot-starter:4.1.0


// X remove useless captions
// X find all `.java` mentions
// X validate that all includes work or resolve
// X find all mentions of Kotlin, just in case
// make sure we show the bullet form of the dependencies
// X find all mentions of Graal
String bookFolderRoot = "/secure-all-the-things";
String home = System.getenv("HOME");
String bookRoot = home + "/code/" + bookFolderRoot + "/book";
String codeRoot = home + "/code/" + bookFolderRoot;

List<Path> findAdocs(String rootPath) throws IOException {
    try (var stream = Files.walk(Path.of(rootPath))) {
        return stream
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".adoc"))
                .collect(Collectors.toList());
    }
}

void findInlineDependencyWithNoSpaces(Path path, String doc) {
    var m = Pattern.compile("`.*`:`.*`").matcher(doc);
    var deps = new ArrayList<String>();
    while (m.find())
        deps.add(m.group());
    IO.println(String.join(System.lineSeparator(), deps));
}

void findInlineDependency(Path path, String doc) {
    var m = Pattern.compile("`.*`\\s*:\\s*`.*`").matcher(doc);
    var deps = new ArrayList<String>();
    while (m.find()) deps.add(m.group());
    IO.println(String.join(System.lineSeparator(), deps));
}

void findJavaMentions(Path path, String adocBody) {
    var m = Pattern.compile(".*\\.java").matcher(adocBody);
    var javas = new ArrayList<String>();
    while (m.find()) javas.add("\t" + m.group());
    IO.println(String.join(System.lineSeparator(), javas));
}

void findReferencesToThingsIDidntWrite(Path path, String body) {
    var words = new String[]{"kotlin", "graal"};
    for (var word : words) {
        var m = Pattern.compile("kotlin", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE).matcher(body);
        var results = new ArrayList<String>();
        while (m.find()) results.add(m.group());
        IO.println(results.size());
        for (String k : results) IO.println(k);
    }
}

void validateIncludes(Path path, String body) {
    var m = Pattern.compile("^include.*", Pattern.MULTILINE).matcher(body);
    while (m.find()) {
        var include = m.group();
        var pathWithColons = include.substring("include".length()).split("\\[")[0];
        assert pathWithColons.startsWith("::");
        var p = pathWithColons.substring(2);
        if (p.startsWith("{code}")) {
            p = codeRoot + p.substring("{code}".length());
            assert Files.exists(Path.of(p)) : "the include path must validate";
        }
    }
}

void findPoms(Path path, String body) {
    var toFind = new String[]{"pom\\.xml", "<dependency"};
    for (var f : toFind) {
        var m = Pattern.compile(f, Pattern.MULTILINE | Pattern.CASE_INSENSITIVE).matcher(body);
        while (m.find()) {
            IO.println("found: %s %n".formatted(m.group()));
        }
    }
}

void main() throws IOException {
    var processors = List.<BiConsumer<Path, String>>of(
            (path, s) -> findInlineDependencyWithNoSpaces(path, s)
            // BookProcessor::findPoms
            // BookProcessor::findReferencesToThingsIDidntWrite
            // BookProcessor::findJavaMentions
            // BookProcessor::validateIncludes
    );
    var adocs = findAdocs(bookRoot);
    IO.println(adocs.stream().map(Path::toString).collect(Collectors.joining(System.lineSeparator())));
    for (var adoc : adocs) {
        var body = Files.readString(adoc, StandardCharsets.UTF_8);
        IO.println("=".repeat(100));
        IO.println(adoc);
        for (var processor : processors) {
            processor.accept(adoc, body);
        }
    }
}