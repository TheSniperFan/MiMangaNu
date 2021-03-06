package ar.rulosoft.mimanganu.services;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import ar.rulosoft.mimanganu.R;
import ar.rulosoft.mimanganu.componentes.Chapter;
import ar.rulosoft.mimanganu.componentes.Database;
import ar.rulosoft.mimanganu.componentes.Manga;
import ar.rulosoft.mimanganu.servers.ServerBase;
import ar.rulosoft.mimanganu.services.ChapterDownload.DownloadStatus;
import ar.rulosoft.mimanganu.services.SingleDownload.Status;

public class DownloadPoolService extends Service implements StateChange {

    private final static int[] illegalChars = {
            34, 60, 62, 124,
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
            21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31,
            58, 42, 63, 92, 47
    };
    public static int SLOTS = 2;
    public static DownloadPoolService actual = null;
    public static ArrayList<ChapterDownload> descargas = new ArrayList<>();
    private static boolean intentPrending = false;
    private static DownloadListener downloadListener = null;


    static {
        Arrays.sort(illegalChars);
    }

    public int slots = SLOTS;

    public static void agregarDescarga(Activity activity, Chapter chapter, boolean lectura) {
        if (!chapter.isDownloaded()) {
            if (descargaNueva(chapter.getId())) {
                ChapterDownload dc = new ChapterDownload(chapter);
                if (lectura)
                    descargas.add(0, dc);
                else
                    descargas.add(dc);
            } else {
                for (ChapterDownload dc : descargas) {
                    if (dc.chapter.getId() == chapter.getId()) {
                        if (dc.status == DownloadStatus.ERROR) {
                            dc.chapter.deleteImages(activity);
                            descargas.remove(dc);
                            dc = null;
                            ChapterDownload ndc = new ChapterDownload(chapter);
                            if (lectura) {
                                descargas.add(0, ndc);
                            } else {
                                descargas.add(ndc);
                            }
                        } else {
                            if (lectura) {
                                descargas.remove(dc);
                                descargas.add(0, dc);
                            }
                        }
                        break;
                    }
                }
            }
            initValues(activity);
            if (!intentPrending && actual == null) {
                intentPrending = true;
                activity.startService(new Intent(activity, DownloadPoolService.class));
            }
        }
    }

    private static void initValues(Context context) {
        SharedPreferences pm = PreferenceManager.getDefaultSharedPreferences(context);
        int descargas = Integer.parseInt(pm.getString("download_threads", "2"));
        int tolerancia = Integer.parseInt(pm.getString("error_tolerancia", "5"));
        int reintentos = Integer.parseInt(pm.getString("reintentos", "4"));
        ChapterDownload.MAX_ERRORS = tolerancia;
        SingleDownload.RETRY = reintentos;
        DownloadPoolService.SLOTS = descargas;
    }

    private static boolean descargaNueva(int cid) {
        boolean result = true;
        for (ChapterDownload dc : descargas) {
            if (dc.chapter.getId() == cid) {
                result = false;
                break;
            }
        }
        return result;
    }

    public static boolean quitarDescarga(int cid, Context c) {
        boolean result = true;
        for (ChapterDownload dc : descargas) {
            if (dc.chapter.getId() == cid) {
                if (dc.status.ordinal() != DownloadStatus.DOWNLOADIND.ordinal()) {
                    descargas.remove(dc);
                } else {
                    Toast.makeText(c, R.string.quitar_descarga, Toast.LENGTH_LONG).show();
                    result = false;
                }
                break;
            }
        }
        return result;
    }

    public static void attachListener(ChapterDownload.OnErrorListener lector, int cid) {
        for (ChapterDownload dc : descargas) {
            if (dc.chapter.getId() == cid) {
                dc.setErrorListener(lector);
                break;
            }
        }
    }

    public static void detachListener(int cid) {
        attachListener(null, cid);
    }

    public static String generarRutaBase(ServerBase s, Manga m, Chapter c, Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String dir = prefs.getString("directorio",
                Environment.getExternalStorageDirectory().getAbsolutePath());
        return dir + "/MiMangaNu/" + cleanFileName(s.getServerName()) + "/" +
                cleanFileName(m.getTitle()).trim() + "/" + cleanFileName(c.getTitle()).trim();
    }

    public static String generarRutaBase(ServerBase s, Manga m, Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String dir = prefs.getString("directorio",
                Environment.getExternalStorageDirectory().getAbsolutePath());
        return dir + "/MiMangaNu/" + cleanFileName(s.getServerName()).trim() + "/" +
                cleanFileName(m.getTitle()).trim();
    }

    private static String cleanFileName(String badFileName) {
        StringBuilder cleanName = new StringBuilder();
        for (int i = 0; i < badFileName.length(); i++) {
            int c = (int) badFileName.charAt(i);
            if (Arrays.binarySearch(illegalChars, c) < 0) {
                cleanName.append((char) c);
            }
        }
        return cleanName.toString();
    }

    public static void setDownloadListener(DownloadListener nDownloadListener) {
        downloadListener = nDownloadListener;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        actual = this;
        intentPrending = false;
        new Thread(new Runnable() {

            @Override
            public void run() {
                iniciarCola();
            }
        }).start();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onChange(SingleDownload singleDownload) {
        if (singleDownload.status.ordinal() > Status.POSTPONED.ordinal()) {
            slots++;
        }
        if (downloadListener != null) {
            downloadListener.onImageDownloaded(singleDownload.cid, singleDownload.index);
        }
    }

    private void iniciarCola() {
        Manga manga = null;
        ServerBase s = null;
        String ruta = "";
        int lcid = -1;
        while (!descargas.isEmpty()) {
            if (slots > 0) {
                slots--;
                ChapterDownload dc = null;
                int sig = 1;
                for (ChapterDownload d : descargas) {
                    if (d.status != DownloadStatus.ERROR) {
                        sig = d.getNext();
                        if (sig > -1) {
                            dc = d;
                            break;
                        }
                    }
                }
                if (dc != null) {
                    if (manga == null || manga.getId() != dc.chapter.getMangaID()) {
                        manga = Database.getManga(actual.getApplicationContext(), dc.chapter.getMangaID());
                        s = ServerBase.getServer(manga.getServerId());
                    }
                    if (lcid != dc.chapter.getId()) {
                        lcid = dc.chapter.getId();
                        ruta = generarRutaBase(s, manga, dc.chapter, getApplicationContext());
                        new File(ruta).mkdirs();
                    }
                    try {
                        String origen = s.getImageFrom(dc.chapter, sig);
                        String destino = ruta + "/" + sig + ".jpg";
                        SingleDownload des =
                                new SingleDownload(origen, destino, sig - 1, dc.chapter.getId());
                        des.setChangeListener(dc);
                        dc.setChagesListener(this);
                        new Thread(des).start();
                    } catch (Exception e) {
                        dc.setErrorIdx(sig - 1);
                        slots++;
                    }
                } else if (slots == 1) {
                    break;
                } else {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    slots++;
                }
            } else {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        actual = null;
        stopSelf();
    }
}
