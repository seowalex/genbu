package genbu.writer;

public sealed interface IndentationStyle {
    record SPACE(int size) implements IndentationStyle {
        @Override
        public String toString() {
            return " ".repeat(size);
        }
    }

    static SPACE SPACE(int size) {
        return new SPACE(size);
    }

    record TAB() implements IndentationStyle {
        @Override
        public String toString() {
            return "\t";
        }
    }

    final static TAB TAB = new TAB();
}
