package org.ctc.testsupport;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TagFilter;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

public final class CiTestInventory {
    private CiTestInventory() { }

    static String root(TestIdentifier identifier) {
        return identifier.getUniqueIdObject().getSegments().stream()
                .filter(segment -> segment.getType().equals("class"))
                .map(segment -> segment.getValue()).findFirst().orElse(null);
    }

    public static void main(String[] args) throws Exception {
        Path classes = Path.of("target/test-classes");
        List<String> candidates;
        try (var files = Files.walk(classes)) {
            candidates = files.filter(path -> path.toString().endsWith(".class"))
                    .map(path -> classes.relativize(path).toString().replace('/', '.').replace('\\', '.'))
                    .map(name -> name.substring(0, name.length() - 6))
                    .filter(name -> !name.contains("$") && runnerCandidate(name))
                    .sorted().toList();
        }
        Map<String, String> stages = new HashMap<>();
        Map<String, Boolean> databases = new HashMap<>();
        Set<String> all = discover(candidates, "all", stages, databases, new ArrayList<>());
        Set<String> routed = new HashSet<>();
        List<String> output = new ArrayList<>();
        for (String stage : List.of("unit", "integration", "browser")) {
            for (String id : discover(candidates, stage, stages, databases, output)) {
                if (!routed.add(id)) throw new IllegalStateException("Duplicate test routing: " + id);
            }
        }
        if (!all.equals(routed)) {
            all.removeAll(routed);
            throw new IllegalStateException("JUnit tests missing from canonical tag/name routing: " + all);
        }
        Files.write(Path.of(args[0]), output);
    }

    private static boolean runnerCandidate(String name) {
        String simple = name.substring(name.lastIndexOf('.') + 1);
        return simple.startsWith("Test") || simple.endsWith("Test") || simple.endsWith("Tests")
                || simple.endsWith("TestCase") || simple.startsWith("IT") || simple.endsWith("IT")
                || simple.endsWith("ITCase");
    }

    private static Set<String> discover(List<String> candidates, String stage, Map<String, String> stages,
            Map<String, Boolean> databases, List<String> output) throws Exception {
        var request = LauncherDiscoveryRequestBuilder.request()
                .filters(TagFilter.excludeTags("flaky"));
        for (String name : candidates) {
            String simple = name.substring(name.lastIndexOf('.') + 1);
            boolean included = switch (stage) {
                case "unit" -> simple.startsWith("Test") || simple.endsWith("Test")
                        || simple.endsWith("Tests") || simple.endsWith("TestCase");
                case "integration" -> simple.startsWith("IT") || simple.endsWith("IT") || simple.endsWith("ITCase");
                case "browser" -> name.startsWith("org.ctc.e2e.") && simple.endsWith("Test");
                default -> true;
            };
            if (included) request.selectors(DiscoverySelectors.selectClass(name));
        }
        if (stage.equals("unit")) request.filters(TagFilter.excludeTags("integration", "e2e"));
        if (stage.equals("integration")) request.filters(TagFilter.includeTags("integration"), TagFilter.excludeTags("e2e"));
        if (stage.equals("browser")) request.filters(TagFilter.includeTags("e2e"));
        var plan = LauncherFactory.create().discover(request.build());
        Set<String> ids = new HashSet<>();
        for (var engine : plan.getRoots()) {
            for (var identifier : plan.getDescendants(engine)) {
                if (!(identifier.getSource().orElse(null) instanceof MethodSource)) continue;
                String root = root(identifier);
                if (root == null) throw new IllegalStateException("Method without a class: " + identifier);
                String id = identifier.getUniqueId();
                ids.add(id);
                if (stage.equals("all")) continue;
                String prior = stages.putIfAbsent(root, stage);
                if (prior != null && !prior.equals(stage)) {
                    throw new IllegalStateException("Mixed-stage class requires explicit routing: " + root);
                }
                if (!databases.containsKey(root)) databases.put(root, mariaDb(root));
                String encoded = Base64.getEncoder().encodeToString(id.getBytes(StandardCharsets.UTF_8));
                output.add(root + "\t" + stage + "\t" + databases.get(root) + "\t"
                        + (identifier.isTest() ? "test" : "container") + "\t" + encoded);
            }
        }
        return ids;
    }

    private static boolean mariaDb(String root) throws Exception {
        return mariaDb(Class.forName(root, false, CiTestInventory.class.getClassLoader()), new HashSet<>());
    }

    private static boolean mariaDb(Class<?> type, Set<Class<?>> visited) throws Exception {
        if (type == null || type == Object.class || !visited.add(type)) return false;
        if (type.getName().contains("MariaDb")) return true;
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (var stream = type.getResourceAsStream(resource)) {
            if (stream != null && new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1)
                    .contains("MariaDBContainer")) return true;
        }
        for (Class<?> nested : type.getDeclaredClasses()) {
            if (mariaDb(nested, visited)) return true;
        }
        return mariaDb(type.getSuperclass(), visited);
    }
}
