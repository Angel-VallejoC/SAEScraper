package me.angelvc.saes.scraper;

import me.angelvc.saes.scraper.models.Kardex;
import me.angelvc.saes.scraper.models.KardexClass;
import me.angelvc.saes.scraper.models.ScheduleClass;
import me.angelvc.saes.scraper.models.StudentInfo;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import javafx.util.Pair;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class SAEScraper {

    private static final String BASE_URL = "https://www.saes.upiicsa.ipn.mx/";
    private static final String USER_AGENT = "Chrome/81.0.4044.138";

    private static Map<String, String> cookies;
    private static Document loginDocument;  // stores login page and home page once the user is logged in

    private static SAEScraper parser;

    private SAEScraper() {
        cookies = new HashMap<>();
        loginDocument = null;
    }

    public static SAEScraper getInstance() {
        if (parser == null)
            parser = new SAEScraper();

        return parser;
    }

    public byte[] loadLoginPage() throws IOException {
        // Load the initial page for getting the required cookies
        Connection connection = Jsoup.connect(BASE_URL).method(Connection.Method.GET);
        Connection.Response response = connection.execute();
        loginDocument = response.parse();

        Element captcha = loginDocument.select("#c_default_ctl00_leftcolumn_loginuser_logincaptcha_CaptchaImage").first();
        if (captcha == null) {
            throw new RuntimeException("Unable to find captcha...");
        }

        // store cookies
        cookies.putAll(response.cookies());

        // Fetch the captcha image
        response = Jsoup
                .connect(captcha.absUrl("src")) // Extract image absolute URL
                .cookies(cookies) // Grab cookies
                .ignoreContentType(true) // Needed for fetching image
                .execute();


        return response.bodyAsBytes();
    }

    public Pair<Boolean, String> login(String user, String password, String captcha) throws IOException {
        String actionUrl = "https://www.saes.upiicsa.ipn.mx/Default.aspx?ReturnUrl=%2falumnos%2fdefault.aspx";

        // required parameters to login
        String eventTarget = loginDocument.select("#__EVENTTARGET").first().attr("value");
        String eventArgument = loginDocument.select("#__EVENTARGUMENT").first().attr("value");
        String viewState = loginDocument.select("#__VIEWSTATE").first().attr("value");
        String viewStateGenerator = loginDocument.select("#__VIEWSTATEGENERATOR").first().attr("value");
        String eventValidation = loginDocument.select("#__EVENTVALIDATION").first().attr("value");
        String lbdVCID = loginDocument.select("#LBD_VCID_c_default_ctl00_leftcolumn_loginuser_logincaptcha").first().attr("value");
        String lbdWorkaround = loginDocument.select("#LBD_BackWorkaround_c_default_ctl00_leftcolumn_loginuser_logincaptcha").first().attr("value");
        String loginButton = loginDocument.select("#ctl00_leftColumn_LoginUser_LoginButton").first().attr("value");

        Connection connection = Jsoup.connect(actionUrl).cookies(cookies).method(Connection.Method.POST)
                .userAgent(USER_AGENT)
                .data("__EVENTTARGET", eventTarget)
                .data("__EVENTARGUMENT", eventArgument)
                .data("__VIEWSTATE", viewState)
                .data("__VIEWSTATEGENERATOR", viewStateGenerator)
                .data("__EVENTVALIDATION", eventValidation)
                .data("ctl00$leftColumn$LoginUser$UserName", user)
                .data("ctl00$leftColumn$LoginUser$Password", password)
                .data("ctl00$leftColumn$LoginUser$CaptchaCodeTextBox", captcha)
                .data("LBD_VCID_c_default_ctl00_leftcolumn_loginuser_logincaptcha", lbdVCID)
                .data("LBD_BackWorkaround_c_default_ctl00_leftcolumn_loginuser_logincaptcha", lbdWorkaround)
                .data("ctl00$leftColumn$LoginUser$LoginButton", loginButton);

        Connection.Response response = connection.execute();
        loginDocument = response.parse();

        Element error = loginDocument.selectFirst("#ctl00_leftColumn_LoginUser > tbody > tr > td > span");

        if (error == null) { // there is no error, user is logged in
            cookies.clear();
            cookies.putAll(response.cookies());
            return new Pair<>(true, "");
        }

        return new Pair<>(false, error.text());
    }

    public boolean isLoggedIn() {
        if (loginDocument == null)
            return false;

        return loginDocument.select("#ctl00_leftColumn_LoginStatusSession").first() != null;
    }

    public List<ScheduleClass> getStudentSchedule() throws IOException {
        if (!isLoggedIn())
            throw new IllegalStateException("User must be logged in to get schedule");

        String scheduleUrl = loginDocument.select("#ctl00_subMenun10 > td > table > tbody > tr > td > a")
                .first().absUrl("href");
        Connection connection = Jsoup.connect(scheduleUrl).cookies(cookies)
                .method(Connection.Method.GET).userAgent(USER_AGENT);

        List<ScheduleClass> schedule = new ArrayList<>();

        Connection.Response response = connection.execute();
        Document scheduleDocument = response.parse();
        Elements scheduleTable = scheduleDocument.select("#ctl00_mainCopy_GV_Horario tr:nth-child(n+2)");

        for (Element classRow : scheduleTable) {

            ArrayList<String> classDetails = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                classDetails.add(classRow.select("td:nth-child(" + i + ") font").first().text());
            }

            ScheduleClass scheduleClass = new ScheduleClass(
                    classDetails.get(0),  // grupo
                    classDetails.get(1),  // materia
                    classDetails.get(2),  // profesor
                    classDetails.get(3),  // edificio
                    classDetails.get(4),  // salon
                    new String[]{
                            classDetails.get(5), // Lunes
                            classDetails.get(6), // Martes
                            classDetails.get(7), // Miércoles
                            classDetails.get(8), // Jueves
                            classDetails.get(9), // Viernes
                    }  // horario
            );

            schedule.add(scheduleClass);
        }

        return schedule;
    }

    public StudentInfo getStudentInfo() throws IOException {
        if (!isLoggedIn())
            throw new IllegalStateException("User must be logged in to get the student info");

        String kardexUrl = loginDocument
                .select("#ctl00_subMenun5 > td > table > tbody > tr > td > a")
                .first().absUrl("href");

        Connection connection = Jsoup.connect(kardexUrl).cookies(cookies)
                .method(Connection.Method.GET).userAgent(USER_AGENT);

        Connection.Response response = connection.execute();
        Document studentInfoDocument = response.parse();

        return new StudentInfo(
                studentInfoDocument.select("#banner").text(),
                studentInfoDocument.select("#ctl00_mainCopy_Lbl_Nombre > table > tbody > tr:nth-child(1) > td:nth-child(2)").text(),
                studentInfoDocument.select("#ctl00_mainCopy_Lbl_Nombre > table > tbody > tr:nth-child(2) > td:nth-child(2)").text(),
                studentInfoDocument.select("#ctl00_mainCopy_Lbl_Carrera").text(),
                studentInfoDocument.select("#ctl00_mainCopy_Lbl_Plan").text(),
                studentInfoDocument.select("#ctl00_mainCopy_Lbl_Promedio").text()
        );
    }

    public Kardex getKardex() throws IOException {
        if (!isLoggedIn())
            throw new IllegalStateException("User must be logged in to get student's kardex");

        String kardexUrl = loginDocument.select("#ctl00_subMenun5 > td > table > tbody > tr > td > a")
                .first().absUrl("href");

        Connection connection = Jsoup.connect(kardexUrl).cookies(cookies)
                .method(Connection.Method.GET).userAgent(USER_AGENT);

        Connection.Response response = connection.execute();
        Document kardexDocument = response.parse();
        Elements kardexElements = kardexDocument.select("#ctl00_mainCopy_Lbl_Kardex").first().children();

        Kardex kardex = new Kardex();

        int levelCount = 0;
        for (Element kardexElement : kardexElements) {
            Elements classesTable = kardexElement.select("table > tbody > tr:nth-child(n+3)");
            String levelName = kardexElement.select("table > tbody > tr:nth-child(1) > td").first().ownText();

            for (Element classEntry : classesTable) {
                kardex.addClass(levelCount, levelName, new KardexClass(
                        classEntry.select("td:nth-child(1)").first().ownText(), // clave
                        classEntry.select("td:nth-child(2)").first().ownText(), // materia
                        classEntry.select("td:nth-child(3)").first().ownText(), // fecha
                        classEntry.select("td:nth-child(4)").first().ownText(), // periodo
                        classEntry.select("td:nth-child(5)").first().ownText(), // formaEvaluación
                        classEntry.select("td:nth-child(6)").first().ownText() // calificacion
                ));
            }
            levelCount++;
        }

        return kardex;
    }
}
