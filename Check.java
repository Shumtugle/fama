import io.github.shumtugle.fama.Words;
public class Check {
    public static void main(String[] a) throws Exception {
        String t = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(a[0])), "UTF-8");
        System.out.println("module? " + Words.isModule(t));
        Words.read(t);
        System.out.println(Words.name() + " " + Words.filled() + "/" + Words.total());
        System.out.println(Words.s("words_of").replace("{n}", "29").replace("{m}", "29"));
    }
}
