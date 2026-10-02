package net.ryzlar.strategem;

/** One arrow of a stratagem code. */
public enum StrategemInput {
    UP("↑"),
    DOWN("↓"),
    LEFT("←"),
    RIGHT("→");

    private final String arrow;

    StrategemInput(String arrow) {
        this.arrow = arrow;
    }

    public String arrow() {
        return arrow;
    }
}
