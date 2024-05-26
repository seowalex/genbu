package genbu.writer;

public sealed interface PrefixAlignment {
    int width();

    record LEFT(int width) implements PrefixAlignment {
        @Override
        public String toString() {
            return "-";
        }
    }

    static LEFT LEFT(int width) {
        return new LEFT(width);
    }

    record RIGHT(int width) implements PrefixAlignment {
        @Override
        public String toString() {
            return "";
        }
    }

    static RIGHT RIGHT(int width) {
        return new RIGHT(width);
    }
}
