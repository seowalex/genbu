package genbu.writer;

public sealed interface PrefixAlignment {
    int width();

    record LEFT(int width) implements PrefixAlignment {
        @Override
        public String toString() {
            return "-";
        }
    }

    record RIGHT(int width) implements PrefixAlignment {
        @Override
        public String toString() {
            return "";
        }
    }
}
