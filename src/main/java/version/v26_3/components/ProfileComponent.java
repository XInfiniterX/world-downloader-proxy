package version.v26_3.components;

import version.v26_3.packets.UUID;

import java.util.List;

public record ProfileComponent(
        Kind kind,
        String name,
        UUID id,
        List<Property> properties,
        String texture,
        String cape,
        String elytra,
        Model model
) {
    public ProfileComponent {
        properties = List.copyOf(properties);
    }

    public enum Kind {
        PARTIAL,
        COMPLETE
    }

    public enum Model {
        WIDE,
        SLIM
    }

    public record Property(String name, String value, String signature) { }
}
