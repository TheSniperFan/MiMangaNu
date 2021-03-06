package ar.rulosoft.mimanganu.servers;

import android.text.Html;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ar.rulosoft.mimanganu.R;
import ar.rulosoft.mimanganu.componentes.Chapter;
import ar.rulosoft.mimanganu.componentes.Manga;

public class DeNineMangaCom extends ServerBase {
    private static String HOST = "http://de.ninemanga.com";

    // As you can guess, "V" is missing, but this is the fault of the website, somehow
    private static String[] genre = new String[]{
            "Alle", "0-9",
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
            "N", "O", "P", "Q", "R", "S", "T", "U", "W", "X", "Y", "Z"
    };
    private static String[] genreV = new String[]{
            "index_.html", "0-9_.html",
            "A_.html", "B_.html", "C_.html", "D_.html", "E_.html", "F_.html", "G_.html",
            "H_.html", "I_.html", "J_.html", "K_.html", "L_.html", "M_.html", "N_.html",
            "O_.html", "P_.html", "Q_.html", "R_.html", "S_.html", "T_.html", "U_.html",
            "W_.html", "X_.html", "Y_.html", "Z_.html"
    };
    private static String[] order = new String[]{
            "/category/", "/list/New-Update/", "/list/Hot-Book/", "/list/New-Book/"
    };

    public DeNineMangaCom() {
        this.setFlag(R.drawable.flag_de);
        this.setIcon(R.drawable.esninemanga);
        this.setServerName("DeNineManga");
        setServerID(ServerBase.DENINEMANGA);
    }

    @Override
    public ArrayList<Manga> getMangas() throws Exception {
        return null;
    }

    @Override
    public ArrayList<Manga> search(String term) throws Exception {
        String source = getNavWithHeader().get(
                HOST + "/search/?wd=" + URLEncoder.encode(term, "UTF-8"));
        ArrayList<Manga> mangas = new ArrayList<>();
        Pattern p = Pattern.compile("bookname\" href=\"(/manga/[^\"]+)\">(.+?)<");
        Matcher m = p.matcher(source);
        while (m.find()) {
            Manga manga = new Manga(DENINEMANGA, m.group(2), HOST + m.group(1), false);
            mangas.add(manga);
        }
        return mangas;
    }

    @Override
    public void loadChapters(Manga m, boolean forceReload) throws Exception {
        if (m.getChapters() == null || m.getChapters().size() == 0 || forceReload)
            loadMangaInformation(m, forceReload);
    }

    @Override
    public void loadMangaInformation(Manga m, boolean forceReload) throws Exception {
        String source = getNavWithHeader().get(m.getPath() + "?waring=1");
        // Front
        m.setImages(getFirstMatchDefault("Manga\" src=\"(.+?)\"", source, ""));
        // Summary
        String summary = getFirstMatchDefault("<p itemprop=\"description\">(.+?)</p>",
                source, "Keine inhaltsangabe").replaceAll("<.+?>", "");
        m.setSynopsis(Html.fromHtml(summary.replaceFirst("Zusammenfassung:", "")).toString());
        // Status
        m.setFinished(!getFirstMatchDefault("<b>Status:</b>(.+?)</a>", source, "").contains("Laufende"));
        // Author
        m.setAuthor(getFirstMatchDefault("Autor.+?\">(.+?)<", source, ""));
        // Chapter
        Pattern p = Pattern.compile(
                "<a class=\"chapter_list_a\" href=\"(/chapter.+?)\" title=\"(.+?)\">(.+?)</a>");
        Matcher matcher = p.matcher(source);
        ArrayList<Chapter> chapters = new ArrayList<>();
        while (matcher.find()) {
            chapters.add(0, new Chapter(matcher.group(3), HOST + matcher.group(1)));
        }
        m.setChapters(chapters);
    }

    @Override
    public String getPagesNumber(Chapter c, int page) {
        return c.getPath().replace(".html", "-" + page + ".html");
    }

    @Override
    public String getImageFrom(Chapter c, int page) throws Exception {
        if (c.getExtra() == null)
            setExtra(c);
        String[] images = c.getExtra().split("\\|");
        return images[page];
    }

    private void setExtra(Chapter c) throws Exception {
        String source = getNavWithHeader().get(
                c.getPath().replace(".html", "-" + c.getPages() + "-1.html"));
        Pattern p = Pattern.compile("<img class=\"manga_pic.+?src=\"([^\"]+)");
        Matcher m = p.matcher(source);
        String images = "";
        while (m.find()) {
            images = images + "|" + m.group(1);
        }
        c.setExtra(images);
    }

    @Override
    public void chapterInit(Chapter c) throws Exception {
        String source = getNavWithHeader().get(c.getPath());
        String nop = getFirstMatch(
                "\\d+/(\\d+)</option>[\\s]*</select>", source,
                "Es versäumt, die Anzahl der Seiten zu bekommen");
        c.setPages(Integer.parseInt(nop));
    }

    @Override
    public ArrayList<Manga> getMangasFiltered(int category, int order, int pageNumber) throws Exception {
        String source = getNavWithHeader().get(
                HOST + DeNineMangaCom.order[order] +
                        genreV[category].replace("_", "_" + pageNumber));
        return getMangasFromSource(source);
    }

    private ArrayList<Manga> getMangasFromSource(String source) {
        ArrayList<Manga> mangas = new ArrayList<>();
        Pattern p = Pattern.compile(
                "<a href=\"(/manga/[^\"]+)\"><img src=\"(.+?)\".+?alt=\"([^\"]+)\"");
        Matcher m = p.matcher(source);
        while (m.find()) {
            Manga manga = new Manga(DENINEMANGA, m.group(3), HOST + m.group(1), false);
            manga.setImages(m.group(2));
            mangas.add(manga);
        }
        return mangas;
    }

    @Override
    public String[] getCategories() {
        return genre;
    }

    @Override
    public String[] getOrders() {
        // "/category/", "/list/New-Update/", "/list/Hot-Book", "/list/New-Book/"
        return new String[]{"Manga Liste", "Updates", "Beliebte Manga", "Neue Manga"};
    }

    @Override
    public boolean hasList() {
        return false;
    }
}
