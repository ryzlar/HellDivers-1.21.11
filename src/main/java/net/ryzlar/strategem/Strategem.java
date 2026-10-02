package net.ryzlar.strategem;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A stratagem as it appears in the menu: its code, look and destruction level, plus the {@link Attack} it calls in.
 * Register new ones in {@link Strategems}.
 */
public record Strategem(Identifier id, int color, DestructionLevel level, List<StrategemInput> code, Attack attack) {

    public Component displayName() {
        return Component.translatable("strategem." + id.getNamespace() + "." + id.getPath());
    }

    public String arrows() {
        return code.stream().map(StrategemInput::arrow).collect(Collectors.joining(" "));
    }

    /** True if {@code input} is the start of (or equal to) this stratagem's code. */
    public boolean matchesPrefix(List<StrategemInput> input) {
        return input.size() <= code.size() && code.subList(0, input.size()).equals(input);
    }
}
