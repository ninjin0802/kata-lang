package dev.kata.compiler;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Every source-level diagnostic code in the spec has a golden case that produces it. */
class CodeCoverageTest {
    @Test
    void everyCodeFromE01ToE06HasAGoldenCase() throws Exception {
        String spec = Files.readString(Path.of("..", "docs", "spec", "07-cli-diagnostics.html"));
        TreeSet<String> codes = new TreeSet<>();
        Matcher matcher = Pattern.compile("id=\"(E0[1-6]\\d\\d)\"").matcher(spec);
        while (matcher.find()) {
            codes.add(matcher.group(1));
        }
        assertTrue(codes.size() >= 35, "could not read the codes from the spec: " + codes);
        List<String> missing = new ArrayList<>();
        for (String code : codes) {
            boolean covered = false;
            for (Path dir : GoldenTest.cases()) {
                if (dir.getFileName().toString().startsWith(code + "-")
                        && Files.readString(dir.resolve("expected.json")).contains("\"code\": \"" + code + "\"")) {
                    covered = true;
                }
            }
            if (!covered) {
                missing.add(code);
            }
        }
        assertTrue(missing.isEmpty(), "no golden case for: " + missing);
    }
}
