package me.angelvc.saes.scraper;

import me.angelvc.saes.scraper.exceptions.SessionExpiredException;
import me.angelvc.saes.scraper.models.*;
import me.angelvc.saes.scraper.util.Pair;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class SAEScraper {

    private static final String NULL_DOCUMENT_MESSAGE = "El documento no ha sido inicializado";
    private static final String USER_AGENT = "Chrome/81.0.4044.138";
    private static String BASE_URL;

    private static Map<String, String> cookies;
    private static Document workingDocument;  // stores login page and home page once the user is logged in

    private static SAEScraper scraper;

    private SAEScraper(SAESchoolsUrls.School schoolUrl) {
        cookies = new HashMap<>();
        workingDocument = null;
        BASE_URL = SAESchoolsUrls.getSchoolUrl(schoolUrl);
    }

    /**
     * Obtiene la instancia del scraper
     * @param schoolUrl Escuela a la que pertenece el alumno
     * @return Instancia del scraper
     */
    public static SAEScraper getInstance(SAESchoolsUrls.School schoolUrl) {
        if (scraper == null)
            scraper = new SAEScraper(schoolUrl);

        try {
            scraper.loadLoginPage();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return scraper;
    }

    /**
     * Carga página inicial del SAES.
     * Al llamar a este metodo se limpian las cookies almacenadas previamente.
     * @throws IOException Si existe un error de conexión
     */
    public void loadLoginPage() throws IOException {
        Connection connection = Jsoup.connect(BASE_URL).method(Connection.Method.GET);
        Connection.Response response = connection.execute();
        workingDocument = response.parse();

        // delete previous stored cookies and save the cookies from the new response
        cookies.clear();
        cookies.putAll(response.cookies());
    }

    /**
     * Obtiene la imagen captcha necesaria para iniciar sesión
     * @return Arreglo de bytes de la imagen
     * @throws IllegalStateException Si no se ha cargado la página de inicio de sesión
     * o no es posible encontrar la imagen
     * @throws IOException Si existe un error de conexión
     */
    public byte[] getCaptchaImage() throws IOException {
        if (workingDocument == null)
            throw new IllegalStateException(NULL_DOCUMENT_MESSAGE);

        Element captcha = workingDocument.selectFirst("#c_default_ctl00_leftcolumn_loginuser_logincaptcha_CaptchaImage");
        if (captcha == null) {
            throw new IllegalStateException("No se pudo encontrar el captcha");
        }

        // Fetch the captcha image
        Connection.Response response = Jsoup
                .connect(captcha.absUrl("src")) // Extract image absolute URL
                .cookies(cookies) // Grab cookies
                .ignoreContentType(true) // Needed for fetching image
                .execute();

        return response.bodyAsBytes();
    }

    /**
     * Realiza el inicio de sesión en SAES
     * @param user Usuario/boleta del alumno
     * @param password Constraseña del alumno
     * @param captcha Texto que se muestra en la imagen captcha
     * @return Un objeto {@link me.angelvc.saes.scraper.util.Pair}.
     * Key = true si el login fue exitoso, de lo contrario, false
     * Value contiene el mensaje de error si key = false
     * @throws IOException Si existe un error de conexión
     */
    public Pair<Boolean, String> login(String user, String password, String captcha) throws IOException  {
        if (workingDocument == null)
            throw new IllegalStateException(NULL_DOCUMENT_MESSAGE);
        if (workingDocument.selectFirst("#ctl00_leftColumn_LogOut") != null){
            throw new IllegalStateException("El formulario de inicio de sesión no existe");
        }
        String actionUrl = BASE_URL + "Default.aspx?ReturnUrl=%2falumnos%2fdefault.aspx";

        // required parameters to login
        String eventTarget = workingDocument.selectFirst("#__EVENTTARGET").attr("value");
        String eventArgument = workingDocument.selectFirst("#__EVENTARGUMENT").attr("value");
        String viewState = workingDocument.selectFirst("#__VIEWSTATE").attr("value");
        String viewStateGenerator = workingDocument.selectFirst("#__VIEWSTATEGENERATOR").attr("value");
        String eventValidation = workingDocument.selectFirst("#__EVENTVALIDATION").attr("value");
        String lbdVCID = workingDocument.selectFirst("#LBD_VCID_c_default_ctl00_leftcolumn_loginuser_logincaptcha").attr("value");
        String lbdWorkaround = workingDocument.selectFirst("#LBD_BackWorkaround_c_default_ctl00_leftcolumn_loginuser_logincaptcha").attr("value");
        String loginButton = workingDocument.selectFirst("#ctl00_leftColumn_LoginUser_LoginButton").attr("value");

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
        workingDocument = response.parse();

        Element error = workingDocument.selectFirst("#ctl00_leftColumn_LoginUser > tbody > tr > td > span");

        if (error == null) {
            // there is no error, user is logged in
            // add new generated cookies
            cookies.putAll(response.cookies());
            return new Pair<>(true, "");
        }

        return new Pair<>(false, error.text());
    }

    /**
     * Obtiene el horario del alumno
     * @return Lista de {@link me.angelvc.saes.scraper.models.ScheduleClass}
     * @throws IOException Si existe un error de conexión
     * @throws SessionExpiredException Si la sesión expiró
     */
    public List<ScheduleClass> getStudentSchedule() throws IOException, SessionExpiredException {
        if (workingDocument == null)
            throw new IllegalStateException(NULL_DOCUMENT_MESSAGE);

        String scheduleUrl = workingDocument.selectFirst("#ctl00_subMenun10 > td > table > tbody > tr > td > a")
                .absUrl("href");
        Connection connection = Jsoup.connect(scheduleUrl).cookies(cookies)
                .method(Connection.Method.GET).userAgent(USER_AGENT);

        Connection.Response response = connection.execute();
        checkSessionState(scheduleUrl, response.url().toString());

        Document scheduleDocument = response.parse();
        Elements scheduleTable = scheduleDocument.select("#ctl00_mainCopy_GV_Horario tr:nth-child(n+2)");
        List<ScheduleClass> schedule = new ArrayList<>();
        for (Element classRow : scheduleTable) {
            schedule.add( new ScheduleClass(
                    classRow.selectFirst("td:nth-child(1)").text(),  // grupo
                    classRow.selectFirst("td:nth-child(3)").text(),  // materia
                    classRow.selectFirst("td:nth-child(4)").text(),  // profesor
                    classRow.selectFirst("td:nth-child(5)").text(),  // edificio
                    classRow.selectFirst("td:nth-child(6)").text(),  // salon
                    new String[]{
                            classRow.selectFirst("td:nth-child(7)").text(), // Lunes
                            classRow.selectFirst("td:nth-child(8)").text(), // Martes
                            classRow.selectFirst("td:nth-child(9)").text(), // Miércoles
                            classRow.selectFirst("td:nth-child(10)").text(), // Jueves
                            classRow.selectFirst("td:nth-child(11)").text(), // Viernes
                    }  // horario
            ));
        }

        return schedule;
    }

    /**
     * Obtiene la información general del alumno
     * @return Un objeto {@link me.angelvc.saes.scraper.models.StudentInfo}
     * @throws IOException Si existe un error de conexión
     * @throws SessionExpiredException Si la sesión expiró
     */
    public StudentInfo getStudentInfo() throws IOException, SessionExpiredException {
        if (workingDocument == null)
            throw new IllegalStateException(NULL_DOCUMENT_MESSAGE);

        String kardexUrl = workingDocument
                .selectFirst("#ctl00_subMenun5 > td > table > tbody > tr > td > a").absUrl("href");

        Connection connection = Jsoup.connect(kardexUrl).cookies(cookies)
                .method(Connection.Method.GET).userAgent(USER_AGENT);

        Connection.Response response = connection.execute();
        checkSessionState(kardexUrl, response.url().toString());

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

    /**
     * Obtiene las calificaciones del kardex
     * @return Un objeto {@link me.angelvc.saes.scraper.models.Kardex} con las calificaciones
     * @throws IOException Si existe un error de conexión
     * @throws SessionExpiredException Si la sesión expiró
     */
    public Kardex getKardex() throws IOException, SessionExpiredException {
        if (workingDocument == null)
            throw new IllegalStateException(NULL_DOCUMENT_MESSAGE);

        String kardexUrl = workingDocument.selectFirst("#ctl00_subMenun5 > td > table > tbody > tr > td > a")
                .absUrl("href");

        Connection connection = Jsoup.connect(kardexUrl).cookies(cookies)
                .method(Connection.Method.GET).userAgent(USER_AGENT);

        Connection.Response response = connection.execute();
        checkSessionState(kardexUrl, response.url().toString());

        Document kardexDocument = response.parse();
        Elements kardexElements = kardexDocument.selectFirst("#ctl00_mainCopy_Lbl_Kardex").select("center");

        Kardex kardex = new Kardex();

        int levelCount = 1;
        for (Element kardexElement : kardexElements) {
            Elements classesTable = kardexElement.select("table > tbody > tr:nth-child(n+3)");

            for (Element classEntry : classesTable) {
                String subject = classEntry.selectFirst("td:nth-child(2)").ownText().toLowerCase(); // materia
                subject = subject.substring(0, 1).toUpperCase() + subject.substring(1);
                kardex.addClass(levelCount, new KardexClass(
                        classEntry.selectFirst("td:nth-child(1)").ownText(), // clave
                        subject,
                        classEntry.selectFirst("td:nth-child(3)").ownText(), // fecha
                        classEntry.selectFirst("td:nth-child(4)").ownText(), // periodo
                        classEntry.selectFirst("td:nth-child(5)").ownText(), // formaEvaluación
                        classEntry.selectFirst("td:nth-child(6)").ownText() // calificacion
                ));

            }
            levelCount++;
        }

        return kardex;
    }

    /**
     * Obtiene las calificaciones del semestre actual
     * @return Lista de {@link me.angelvc.saes.scraper.models.GradeEntry}
     * @throws IOException Si existe un error de conexión
     * @throws SessionExpiredException Si la sesión expiró
     */
    public ArrayList<GradeEntry> getGrades() throws IOException, SessionExpiredException {
        if (workingDocument == null)
            throw new IllegalStateException(NULL_DOCUMENT_MESSAGE);

        String gradesUrl = workingDocument.selectFirst("#ctl00_subMenun11 > td > table > tbody > tr > td > a")
                .absUrl("href");

        Connection connection = Jsoup.connect(gradesUrl).cookies(cookies)
                .method(Connection.Method.GET).ignoreHttpErrors(true).userAgent(USER_AGENT);

        Connection.Response response = connection.execute();
        checkSessionState(gradesUrl, response.url().toString());

        Document gradesDocument = response.parse();
        Elements gradesElements = gradesDocument.select("#ctl00_mainCopy_GV_Calif > tbody > tr:nth-child(n+2)");

        if(gradesElements == null)
            return new ArrayList<>();

        ArrayList<GradeEntry> gradeEntries = new ArrayList<>();

        for (Element grade: gradesElements){
            String subject = grade.select("td:nth-child(2)").first().text().toLowerCase();  // materia
            subject = subject.substring(0, 1).toUpperCase() + subject.substring(1);
            gradeEntries.add(new GradeEntry(
                    grade.select("td:nth-child(1)").first().text(),  // grupo
                    subject,
                    grade.select("td:nth-child(3)").first().text(),  // primer parcial
                    grade.select("td:nth-child(4)").first().text(),  // segundo parcial
                    grade.select("td:nth-child(5)").first().text(),  // tercer parcial
                    grade.select("td:nth-child(5)").first().text(),  // extra
                    grade.select("td:nth-child(5)").first().text()   // final
            ));

            return gradeEntries;
        }

        return gradeEntries;
    }

    /**
     * Verifica si la URL de la petición es la misma que se regresa en la respuesta.
     * Si no son iguales significa que la sesión expiró.
     * @param requestUrl String de la URL de la petición
     * @param responseUrl String de la URL de la respuesta
     * @throws SessionExpiredException si las URL's son diferentes
     */
    private void checkSessionState(String requestUrl, String responseUrl) throws SessionExpiredException{
        if (! requestUrl.equals(responseUrl))
            throw new SessionExpiredException("La sesión ha expirado");
    }

}
