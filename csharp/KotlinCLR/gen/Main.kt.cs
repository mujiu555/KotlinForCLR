[global::kotlin.clr.KotlinFileClass]
public static class MainKt
{
    public static void main()
    {
        int a = 1;
        string s1 = $"{("a is ")}{(a)}";
        a = 2;
        string s2 = $"{(global::kotlin.text.TextH.replace(s1, "is", "was"))}{(", but now is ")}{(a)}";
        global::kotlin.io.ConsoleKt.println((s1) + (s2));
    }

    public static void Main([global::kotlin.clr.KotlinNotNull] string[] args)
    {
        global::MainKt.main();
    }
}