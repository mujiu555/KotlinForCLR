[global::kotlin.clr.KotlinFileClass]
public static class MainKt
{
    private static global::System.Double PI_backingField;

    private static global::System.Int32 x_backingField;

    public static global::System.Double PI
    {
        get
        {
            return PI_backingField;
        }
    }

    public static global::System.Int32 x
    {
        get
        {
            return x_backingField;
        }
        set
        {
            x_backingField = value;
        }
    }

    public static void incrementX()
    {
        global::MainKt.x = (global::MainKt.x) + (1);
    }

    public static void main()
    {
        global::kotlin.io.ConsoleKt.println($"{("x = ")}{(global::MainKt.x)}{(", PI = ")}{(global::MainKt.PI)}");
        global::MainKt.incrementX();
        global::kotlin.io.ConsoleKt.println($"{("x = ")}{(global::MainKt.x)}{(", PI = ")}{(global::MainKt.PI)}");
    }

    public static void Main(global::System.String[] args)
    {
        global::MainKt.main();
    }
}