package genbu.model;

import java.util.ArrayList;
import java.util.List;

public record Comments(List<String> suffix, List<String> after) {
    public Comments() {
        this(new ArrayList<>(), new ArrayList<>());
    }

    public void merge(Comments other) {
        if (suffix.size() + other.suffix.size() > 1) {
            throw new UnsupportedOperationException("Only one suffix comment allowed per value");
        }

        suffix.addAll(other.suffix);
        after.addAll(other.after);
    }
}
