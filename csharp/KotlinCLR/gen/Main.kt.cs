[global::kotlin.clr.KotlinFileClass]
public static class MainKt
{
    public static void main()
    {
        global::System.Int32 a = 1;
        global::System.String s1 = $"{("a is ")}{(a)}";
        a = 2;
        global::System.String s2 = $"{(global::kotlin.text.TextH.replace(s1, s1, "is", "was"))}{(", but now is ")}{(a)}";
    }

    public static void Main([global::kotlin.clr.KotlinNotNull] global::System.String[] args)
    {
        global::MainKt.main();
    }
}