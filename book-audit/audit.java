//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
//DEPS org.springframework.boot:spring-boot-starter:4.1.0
//DEPS org.asciidoctor:asciidoctorj:3.0.0

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Attributes;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;
import org.asciidoctor.log.LogHandler;
import org.asciidoctor.log.LogRecord;
import org.asciidoctor.log.Severity;
import org.springframework.util.Assert;

record Errors(Collection<String> unresolvedIncludes,
              Collection<String> unresolvedCallouts) {
}

interface ErrorClassifier
        extends BiConsumer<Errors, LogRecord> {
}

void unresolvedCallout(Errors errors, LogRecord message) {
    if (message.getMessage().contains("no callout found for"))
        errors.unresolvedCallouts().add(context(message));
}

private String context(LogRecord record) {
    return record.getCursor() + "::" + record.getMessage();
}

void unresolvedInclude(Errors errors, LogRecord message) {
    if (message.getMessage().contains("include file not found:"))
        errors.unresolvedIncludes().add(context(message));
}

Errors validate(File adoc, File codeRootFolder) {
    var analysers = List.<ErrorClassifier>of(this::unresolvedCallout, this::unresolvedInclude);
    var errors = new Errors(new ArrayList<>(), new ArrayList<>());
    IO.println("inspecting " + adoc.getAbsolutePath() + " with {code} value " + codeRootFolder.getAbsolutePath());
    try (var asciidoctor = Asciidoctor.Factory.create()) {
        var handler = (LogHandler) record -> {
            if (record.getSeverity() == Severity.ERROR || record.getSeverity() == Severity.WARN)
                for (var analyser : analysers)
                    analyser.accept(errors, record);
        };
        asciidoctor.registerLogHandler(handler);
        var attributes = Attributes.builder()
                .attribute("code", codeRootFolder.getAbsolutePath())
                .build();
        var options = Options.builder()
                .safe(SafeMode.UNSAFE)          // needed so include:: directives are processed
                .baseDir(adoc.getParentFile())
                .toFile(false)                  // render to string, not to disk
                .attributes(attributes)
                .build();
        asciidoctor.convertFile(adoc, options); // we don't care about the result
    }
    return errors;
}

void main() throws Exception {
    var root = new File("../..").getCanonicalFile();
    IO.println("root folder is " + root.getAbsolutePath());
    try (var stream = Files.walk(root.toPath())) {
        var adocs = stream
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".adoc"))
                .toList();
        for (var input : adocs)
            validateAdoc(input.toFile(), root);
    }
}

private void validateAdoc(File input, File root) {
    Assert.state(input.exists(), "input file must exist");
    Assert.state(root.exists(), "code folder must exist");

    var errors = validate(input, root);

    for (var entry : errors.unresolvedCallouts())
        IO.println("missing callouts: " + entry);

    for (var entry : errors.unresolvedIncludes())
        IO.println("missing include: " + entry);
}