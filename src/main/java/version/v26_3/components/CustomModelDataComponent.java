package version.v26_3.components;

import java.util.List;

public record CustomModelDataComponent(
        List<Float> floats,
        List<Boolean> flags,
        List<String> strings,
        int[] colors
) {
    public CustomModelDataComponent {
        floats = List.copyOf(floats);
        flags = List.copyOf(flags);
        strings = List.copyOf(strings);
        colors = colors.clone();
    }

    @Override
    public int[] colors() {
        return colors.clone();
    }
}
