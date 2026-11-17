package io.github.trini3.landmass.config;

import io.github.trini3.landmass.Landmass;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A small TOML config file with comments, sections and checked values, written in the same layout NeoForge uses, so a
 * config file can be copied between the Fabric and NeoForge versions of the mod. Values outside their range, or
 * unreadable ones, fall back to the default, and the file is rewritten with every option and its comment.
 */
public final class ConfigSpec {
    private final List<Entry<?>> entries;

    private ConfigSpec(List<Entry<?>> entries) {
        this.entries = entries;
    }

    /** Reads the file (creating it if missing), then writes it back complete and tidy. */
    public void load(Path file) {
        Map<String, String> raw = new HashMap<>();
        if (Files.exists(file)) {
            try {
                String section = "";
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String text = line.strip();
                    if (text.isEmpty() || text.startsWith("#")) {
                        continue;
                    }
                    if (text.startsWith("[") && text.endsWith("]")) {
                        section = text.substring(1, text.length() - 1).strip() + ".";
                        continue;
                    }
                    int equals = text.indexOf('=');
                    if (equals > 0) {
                        raw.put(section + text.substring(0, equals).strip(), text.substring(equals + 1).strip());
                    }
                }
            } catch (IOException e) {
                Landmass.LOGGER.warn("Couldn't read {}, using the defaults", file, e);
            }
        }
        for (Entry<?> entry : this.entries) {
            String value = raw.get(entry.path);
            if (value == null || !entry.parse(value)) {
                entry.reset();
                if (value != null) {
                    Landmass.LOGGER.warn("Config value {} = {} in {} isn't valid, using the default", entry.path, value, file);
                }
            }
        }
        this.write(file);
    }

    private void write(Path file) {
        StringBuilder out = new StringBuilder();
        String section = null;
        for (Entry<?> entry : this.entries) {
            if (!entry.section.equals(section)) {
                section = entry.section;
                for (Map.Entry<String, List<String>> sectionComment : entry.sectionComments) {
                    out.append('\n');
                    sectionComment.getValue().forEach(line -> out.append("#").append(line).append('\n'));
                    out.append('[').append(sectionComment.getKey()).append("]\n");
                }
            }
            String indent = "\t".repeat(entry.depth);
            entry.comment.forEach(line -> out.append(indent).append("#").append(line).append('\n'));
            entry.rangeComment().ifPresent(line -> out.append(indent).append("#").append(line).append('\n'));
            out.append(indent).append(entry.name).append(" = ").append(entry.format()).append('\n');
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, out.toString().stripLeading(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Landmass.LOGGER.warn("Couldn't write {}", file, e);
        }
    }

    public static final class Builder {
        private final List<Entry<?>> entries = new ArrayList<>();
        private final List<String> path = new ArrayList<>();
        private List<String> comment = List.of();
        // Sections opened since the last option, with their comments, written above that option.
        private final List<Map.Entry<String, List<String>>> pendingSections = new ArrayList<>();

        public Builder comment(String... lines) {
            this.comment = List.of(lines);
            return this;
        }

        public Builder push(String name) {
            this.path.add(name);
            this.pendingSections.add(Map.entry(String.join(".", this.path), this.comment));
            this.comment = List.of();
            return this;
        }

        public Builder pop() {
            this.path.removeLast();
            return this;
        }

        private <T, E extends Entry<T>> E add(E entry) {
            this.entries.add(entry);
            this.comment = List.of();
            this.pendingSections.clear();
            return entry;
        }

        private String section() {
            return String.join(".", this.path);
        }

        private List<Map.Entry<String, List<String>>> sections() {
            return List.copyOf(this.pendingSections);
        }

        public IntValue defineInRange(String name, int defaultValue, int min, int max) {
            return this.add(new IntValue(this, name, defaultValue, min, max));
        }

        public DoubleValue defineInRange(String name, double defaultValue, double min, double max) {
            return this.add(new DoubleValue(this, name, defaultValue, min, max));
        }

        public <E extends Enum<E>> EnumValue<E> defineEnum(String name, E defaultValue) {
            return this.add(new EnumValue<>(this, name, defaultValue));
        }

        public ConfigValue<List<? extends String>> defineListAllowEmpty(String name, List<String> defaultValue, Supplier<String> newElement,
                                                                    Predicate<Object> validator) {
            return this.add(new ListValue(this, name, defaultValue, validator));
        }

        public ConfigSpec build() {
            return new ConfigSpec(List.copyOf(this.entries));
        }
    }

    /** One option. {@link #get()} gives its current value. */
    public abstract static class Entry<T> implements Supplier<T> {
        final String name;
        final String path;
        final String section;
        final int depth;
        final List<String> comment;
        final List<Map.Entry<String, List<String>>> sectionComments;
        final T defaultValue;
        volatile T value;

        Entry(Builder builder, String name, T defaultValue) {
            this.name = name;
            this.section = builder.section();
            this.path = this.section.isEmpty() ? name : this.section + "." + name;
            this.depth = builder.path.size();
            this.comment = builder.comment;
            this.sectionComments = builder.sections();
            this.defaultValue = defaultValue;
            this.value = defaultValue;
        }

        @Override
        public T get() {
            return this.value;
        }

        void reset() {
            this.value = this.defaultValue;
        }

        abstract boolean parse(String text);

        abstract String format();

        java.util.Optional<String> rangeComment() {
            return java.util.Optional.empty();
        }
    }

    public static class ConfigValue<T> extends Entry<T> {
        ConfigValue(Builder builder, String name, T defaultValue) {
            super(builder, name, defaultValue);
        }

        @Override
        boolean parse(String text) {
            return false;
        }

        @Override
        String format() {
            return String.valueOf(this.value);
        }
    }

    public static final class IntValue extends ConfigValue<Integer> {
        private final int min;
        private final int max;

        IntValue(Builder builder, String name, int defaultValue, int min, int max) {
            super(builder, name, defaultValue);
            this.min = min;
            this.max = max;
        }

        @Override
        boolean parse(String text) {
            try {
                int parsed = Integer.parseInt(text.replace("_", ""));
                if (parsed < this.min || parsed > this.max) {
                    return false;
                }
                this.value = parsed;
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        @Override
        java.util.Optional<String> rangeComment() {
            return java.util.Optional.of("Range: " + this.min + " ~ " + this.max);
        }
    }

    public static final class DoubleValue extends ConfigValue<Double> {
        private final double min;
        private final double max;

        DoubleValue(Builder builder, String name, double defaultValue, double min, double max) {
            super(builder, name, defaultValue);
            this.min = min;
            this.max = max;
        }

        @Override
        boolean parse(String text) {
            try {
                double parsed = Double.parseDouble(text.replace("_", ""));
                if (!(parsed >= this.min && parsed <= this.max)) {
                    return false;
                }
                this.value = parsed;
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        @Override
        java.util.Optional<String> rangeComment() {
            return java.util.Optional.of("Range: " + this.min + " ~ " + this.max);
        }
    }

    public static final class EnumValue<E extends Enum<E>> extends ConfigValue<E> {
        EnumValue(Builder builder, String name, E defaultValue) {
            super(builder, name, defaultValue);
        }

        @Override
        boolean parse(String text) {
            String name = unquote(text).toUpperCase(Locale.ROOT);
            for (E constant : this.defaultValue.getDeclaringClass().getEnumConstants()) {
                if (constant.name().equals(name)) {
                    this.value = constant;
                    return true;
                }
            }
            return false;
        }

        @Override
        String format() {
            return "\"" + this.value.name() + "\"";
        }

        @Override
        java.util.Optional<String> rangeComment() {
            StringBuilder allowed = new StringBuilder("Allowed Values: ");
            E[] constants = this.defaultValue.getDeclaringClass().getEnumConstants();
            for (int i = 0; i < constants.length; i++) {
                allowed.append(i == 0 ? "" : ", ").append(constants[i].name());
            }
            return java.util.Optional.of(allowed.toString());
        }
    }

    static final class ListValue extends ConfigValue<List<? extends String>> {
        private final Predicate<Object> validator;

        ListValue(Builder builder, String name, List<String> defaultValue, Predicate<Object> validator) {
            super(builder, name, defaultValue);
            this.validator = validator;
        }

        @Override
        boolean parse(String text) {
            if (!text.startsWith("[") || !text.endsWith("]")) {
                return false;
            }
            List<String> items = new ArrayList<>();
            String inner = text.substring(1, text.length() - 1).strip();
            if (!inner.isEmpty()) {
                for (String part : inner.split(",")) {
                    String item = unquote(part.strip());
                    if (item.isEmpty() && part.isBlank()) {
                        continue;
                    }
                    if (!this.validator.test(item)) {
                        return false;
                    }
                    items.add(item);
                }
            }
            this.value = List.copyOf(items);
            return true;
        }

        @Override
        String format() {
            StringBuilder out = new StringBuilder("[");
            List<? extends String> items = this.value;
            for (int i = 0; i < items.size(); i++) {
                out.append(i == 0 ? "" : ", ").append('"').append(items.get(i)).append('"');
            }
            return out.append(']').toString();
        }
    }

    private static String unquote(String text) {
        return text.length() >= 2 && (text.startsWith("\"") && text.endsWith("\"") || text.startsWith("'") && text.endsWith("'"))
                ? text.substring(1, text.length() - 1) : text;
    }
}
