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

        List<ScheduleClass> schedule = new ArrayList<>();

        Connection.Response response = connection.execute();
        checkSessionState(scheduleUrl, response.url().toString());

        Document scheduleDocument = response.parse();
        Elements scheduleTable = scheduleDocument.select("#ctl00_mainCopy_GV_Horario tr:nth-child(n+2)");
        for (Element classRow : scheduleTable) {

            ArrayList<String> classDetails = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                classDetails.add(classRow.selectFirst("td:nth-child(" + i + ") font").text());
            }

            ScheduleClass scheduleClass = new ScheduleClass(
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
            );

            schedule.add(scheduleClass);
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
        Elements kardexElements = kardexDocument.selectFirst("#ctl00_mainCopy_Lbl_Kardex").select("center");// .children();

        Kardex kardex = new Kardex();

        int levelCount = 1;
        for (Element kardexElement : kardexElements) {
            String levelName = kardexElement.selectFirst("table > tbody > tr:nth-child(1) > td").ownText();
            Elements classesTable = kardexElement.select("table > tbody > tr:nth-child(n+3)");

            for (Element classEntry : classesTable) {
                String subject = classEntry.selectFirst("td:nth-child(2)").ownText().toLowerCase(); // materia
                subject = subject.substring(0, 1).toUpperCase() + subject.substring(1);
                kardex.addClass(levelCount, levelName, new KardexClass(
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

    public ArrayList<GradeEntry> getGradesTest() throws IOException {

        Document gradesDocument = Jsoup.parse("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n<!-- saved from url=(0085)https://www.saes.upiicsa.ipn.mx/Alumnos/Informacion_semestral/calificaciones_sem.aspx -->\n<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"es\" xml:lang=\"es\"><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"><link href=\"./Calificaciones del semestre SAES_files/Default.css\" type=\"text/css\" rel=\"stylesheet\"><link href=\"./Calificaciones del semestre SAES_files/Default(1).css\" type=\"text/css\" rel=\"stylesheet\"><title>\n\tCalificaciones del semestre\n</title>\t\n    \n\t<!--[if IE 5]>\n\t<style type=\"text/css\">\t\t\n\t\t.sidebarcontainer td, .sidebarcontainer tr {font-size:smaller;}\t\t\n\t\t#subnav a {height:1em;}\n\t    body td, body th {font-size:0.78em;}\t\t\n\t</style>\n\t<![endif]-->\n\t<!--[if IE 7]>\n\t<style type=\"text/css\">\t\t        \n       #rightcolumn{margin-right: -152px;}\n\t</style>\n\t<![endif]-->\n<style type=\"text/css\">\n\t.ctl00_mainmenu_0 { background-color:white;visibility:hidden;display:none;position:absolute;left:0px;top:0px; }\n\t.ctl00_mainmenu_1 { text-decoration:none; }\n\t.ctl00_mainmenu_2 {  }\n" +
                "\t.ctl00_mainmenu_3 { border-style:none; }\n" +
                "\t.ctl00_mainmenu_4 {  }\n" +
                "\t.ctl00_mainmenu_5 { background-color:Transparent; }\n" +
                "\t.ctl00_mainmenu_6 { border-style:none; }\n" +
                "\t.ctl00_mainmenu_7 {  }\n" +
                "\t.ctl00_mainmenu_8 { border-style:none; }\n" +
                "\t.ctl00_mainmenu_9 {  }\n" +
                "\t.ctl00_subMenu_0 { background-color:white;visibility:hidden;display:none;position:absolute;left:0px;top:0px; }\n" +
                "\t.ctl00_subMenu_1 { text-decoration:none; }\n" +
                "\t.ctl00_subMenu_2 { width:24px; }\n" +
                "\t.ctl00_subMenu_3 { border-style:none; }\n" +
                "\t.ctl00_subMenu_4 {  }\n" +
                "\t.ctl00_subMenu_5 { background-color:Transparent;width:150px; }\n" +
                "\t.ctl00_subMenu_6 { color:Silver;border-style:none; }\n" +
                "\t.ctl00_subMenu_7 { border-color:Green;border-style:Solid; }\n" +
                "\t.ctl00_subMenu_8 { background-color:Transparent;border-color:Green;border-style:Solid;width:150px; }\n" +
                "\t.ctl00_subMenu_9 { border-style:none; }\n" +
                "\t.ctl00_subMenu_10 {  }\n" +
                "\t.ctl00_subMenu_11 { color:#FFFFC0;font-weight:bold;border-style:none; }\n" +
                "\t.ctl00_subMenu_12 {  }\n" +
                "\t.ctl00_subMenu_13 { border-style:none; }\n" +
                "\t.ctl00_subMenu_14 {  }\n" +
                "\t.ctl00_subMenu_15 { border-style:none; }\n" +
                "\t.ctl00_subMenu_16 { background-color:White; }\n" +
                "\n" +
                "</style></head>\n" +
                "<body style=\"text-align: center;background-image : none !important;background-repeat: initial !important;font-family: Verdana, Geneva, Arial, Helvetica, sans-serif;font-size: 0.78em; \">\n" +
                "\t<form name=\"aspnetForm\" method=\"post\" action=\"https://www.saes.upiicsa.ipn.mx/Alumnos/Informacion_semestral/calificaciones_sem.aspx\" id=\"aspnetForm\">\n" +
                "<div>\n" +
                "<input type=\"hidden\" name=\"__EVENTTARGET\" id=\"__EVENTTARGET\" value=\"\">\n" +
                "<input type=\"hidden\" name=\"__EVENTARGUMENT\" id=\"__EVENTARGUMENT\" value=\"\">\n" +
                "<input type=\"hidden\" name=\"__VIEWSTATE\" id=\"__VIEWSTATE\" value=\"/wEPDwULLTE4MjYwNzUyNTYPZBYCZg9kFgICAw9kFgoCAQ8PFgIeCEltYWdlVXJsBRV+L0ltYWdlcy9sb2dvcy82MC5wbmdkZAIFDzwrAA0CAA8WAh4LXyFEYXRhQm91bmRnZAwUKwACBQMwOjAUKwACFhAeBFRleHQFBkluaWNpbx4FVmFsdWUFBkluaWNpbx4LTmF2aWdhdGVVcmwFDS9kZWZhdWx0LmFzcHgeB1Rvb2xUaXAFBkluaWNpbx4HRW5hYmxlZGceClNlbGVjdGFibGVnHghEYXRhUGF0aAUNL2RlZmF1bHQuYXNweB4JRGF0YUJvdW5kZxQrAAYFEzA6MCwwOjEsMDoyLDA6MywwOjQUKwACFhIfAwUHQWx1bW5vcx8JZx4IU2VsZWN0ZWRnHwIFB0FsdW1ub3MfBAUVL0FsdW1ub3MvZGVmYXVsdC5hc3B4HwZnHwdnHwUFB0FsdW1ub3MfCAUVL2FsdW1ub3MvZGVmYXVsdC5hc3B4ZBQrAAIWEB8CBQpBY2Fkw6ltaWNhHwMFCkFjYWTDqW1pY2EfBAUXL0FjYWRlbWljYS9kZWZhdWx0LmFzcHgfBQUKQWNhZMOpbWljYR8GZx8HZx8IBRcvYWNhZGVtaWNhL2RlZmF1bHQuYXNweB8JZ2QUKwACFhAfAgUKUmVnbGFtZW50bx8DBQpSZWdsYW1lbnRvHwQFGC9SZWdsYW1lbnRvL0RlZmF1bHQuYXNweB8FBQpSZWdsYW1lbnRvHwZnHwdnHwgFGC9yZWdsYW1lbnRvL2RlZmF1bHQuYXNweB8JZ2QUKwACFhAfAgUFQXl1ZGEfAwUFQXl1ZGEfBAURL0F5dWRhL0F5dWRhLmFzcHgfBQUFQXl1ZGEfBmcfB2cfCAURL2F5dWRhL2F5dWRhLmFzcHgfCWdkFCsAAhYQHwIFFVJlY3VwZXJhciBDb250cmFzZcOxYR8DBRVSZWN1cGVyYXIgQ29udHJhc2XDsWEfBAUeL1NlbmRFbWFpbC9QcnVlYmFTZW5kTWFpbC5hc3B4HwUFGFJlY3VwZXJhciBsYSBDb250cmFzZcOxYR8GZx8HZx8IBR4vc2VuZGVtYWlsL3BydWViYXNlbmRtYWlsLmFzcHgfCWdkZAIID2QWCgIBDw8WAh8CBekEPGNlbnRlcj4gICAgPHRhYmxlIHdpZHRoPScxMDAlJz4gICAgICAgIDx0cj4gICAgICAgICAgICA8dGQgcm93c3Bhbj0nMicgYWxpZ249J2NlbnRlcic+ICAgICAgICAgICAgICAgIDxpbWcgc3JjPScuLi8uLi9JbWFnZXMvTG9nb3MvUG9saV9YQ0gucG5nJyBhbHQ9J0lQTicgLz4gICAgICAgICAgICA8L3RkPiAgICAgICAgICAgIDx0ZCBhbGlnbj0nY2VudGVyJz4gICAgICAgICAgICAgICAgPGI+VU5JREFEIFBST0ZFU0lPTkFMIElOVEVSRElTQ0lQTElOQVJJQSBERSBJTkdFTklFUsONQSBZPC9iPiAgICAgICAgICAgIDwvdGQ+ICAgICAgICAgICAgPHRkIHJvd3NwYW49JzInIGFsaWduPSdjZW50ZXInPiAgICAgICAgICAgICAgICA8aW1nIHNyYz0nLi4vLi4vSW1hZ2VzL0xvZ29zLzYwLnBuZycgYWx0PSdFc2N1ZWxhJyBIZWlnaHQ9JzYwJyBXaWR0aD0nNjAnIC8+ICAgICAgICAgICAgPC90ZD4gICAgICAgIDwvdHI+ICAgICAgICA8dHI+ICAgICAgICAgICAgPHRkIGFsaWduPSdjZW50ZXInPiAgICAgICAgICAgICAgICA8Yj5DSUVOQ0lBUyBTT0NJQUxFUyBZIEFETUlOSVNUUkFUSVZBUzwvYj4gICAgICAgICAgICA8L3RkPiAgICAgICAgPC90cj4gICAgPC90YWJsZT48L2NlbnRlcj5kZAIDDw8WAh8CBcsCICAgIDx0YWJsZT4gICAgICAgIDx0cj4gICAgICAgICAgICA8dGQ+ICAgICAgICAgICAgICAgIDxiPkJPTEVUQTogPC9iPiAgICAgICAgICAgIDwvdGQ+ICAgICAgICAgICAgPHRkPiAgICAgICAgICAgICAgICAyMDE0MDMxMzkxICAgICAgICAgICAgPC90ZD4gICAgICAgIDwvdHI+ICAgICAgICA8dHI+ICAgICAgICAgICAgPHRkPiAgICAgICAgICAgICAgICA8Yj5OT01CUkU6IDwvYj4gICAgICAgICAgICA8L3RkPiAgICAgICAgICAgIDx0ZD4gICAgICAgICAgICAgICAgQU5HRUwgVkFMTEVKTyBDSEFQQVJSTyAgICAgICAgICAgIDwvdGQ+ICAgICAgICA8L3RyPiAgICA8L3RhYmxlPmRkAgUPDxYCHwJlZGQCBw8PFgIfAmVkZAIJDzwrAA0CAA8WBh8BZx4LXyFJdGVtQ291bnQCCx4HVmlzaWJsZWdkDBQrAAcWCB4ETmFtZQUFR3J1cG8eCklzUmVhZE9ubHloHgRUeXBlGSsCHglEYXRhRmllbGQFBUdydXBvFggfDQUHTWF0ZXJpYR8OaB8PGSsCHxAFB01hdGVyaWEWCB8NBQsxZXIgUGFyY2lhbB8OaB8PGSsCHxAFCzFlciBQYXJjaWFsFggfDQUKMm8gUGFyY2lhbB8OaB8PGSsCHxAFCjJvIFBhcmNpYWwWCB8NBQszZXIgUGFyY2lhbB8OaB8PGSsCHxAFCzNlciBQYXJjaWFsFggfDQUDRXh0Hw5oHw8ZKwIfEAUDRXh0FggfDQUFRmluYWwfDmgfDxkrAh8QBQVGaW5hbBYCZg9kFhgCAQ9kFg5mDw8WAh8CBQU1QVY4MWRkAgEPDxYCHwIFJUZPUk1VTEFDSU9OIFkgRVZBTFVBQ0lPTiBERSBQUk9ZRUNUT1NkZAICDw8WAh8CBQE5ZGQCAw8PFgIfAgUBOWRkAgQPDxYCHwIFATlkZAIFDw8WAh8CBQJOUGRkAgYPDxYCHwIFATlkZAICD2QWDmYPDxYCHwIFBTROTTYyZGQCAQ8PFgIfAgUSU0VHVVJJREFEIERFIFJFREVTZGQCAg8PFgIfAgUCMTBkZAIDDw8WAh8CBQE5ZGQCBA8PFgIfAgUBOGRkAgUPDxYCHwIFAS1kZAIGDw8WAh8CBQE5ZGQCAw9kFg5mDw8WAh8CBQU0TlY3MGRkAgEPDxYCHwIFFkhBQklMSURBREVTIERJUkVDVElWQVNkZAICDw8WAh8CBQE4ZGQCAw8PFgIfAgUCMTBkZAIEDw8WAh8CBQIxMGRkAgUPDxYCHwIFAk5QZGQCBg8PFgIfAgUBOWRkAgQPZBYOZg8PFgIfAgUFNE5WNzBkZAIBDw8WAh8CBRhWSVJPTE9HSUEgWSBDUklQVE9HUkFGSUFkZAICDw8WAh8CBQE5ZGQCAw8PFgIfAgUBOGRkAgQPDxYCHwIFATlkZAIFDw8WAh8CBQEtZGQCBg8PFgIfAgUBOWRkAgUPZBYOZg8PFgIfAgUFNE5NODBkZAIBDw8WAh8CBRVBUExJQ0FDSU9ORVMgREUgUkVERVNkZAICDw8WAh8CBQIxMGRkAgMPDxYCHwIFAjEwZGQCBA8PFgIfAgUCMTBkZAIFDw8WAh8CBQEtZGQCBg8PFgIfAgUCMTBkZAIGD2QWDmYPDxYCHwIFBTVOTTgwZGQCAQ8PFgIfAgUXSU5GT1JNQVRJQ0EgRU1QUkVTQVJJQUxkZAICDw8WAh8CBQIxMGRkAgMPDxYCHwIFAjEwZGQCBA8PFgIfAgUCMTBkZAIFDw8WAh8CBQIxMGRkAgYPDxYCHwIFAjEwZGQCBw9kFg5mDw8WAh8CBQU1TlY3MGRkAgEPDxYCHwIFFUlOR0VOSUVSSUEgREUgUFJVRUJBU2RkAgIPDxYCHwIFAThkZAIDDw8WAh8CBQIxMGRkAgQPDxYCHwIFAjEwZGQCBQ8PFgIfAgUBLWRkAgYPDxYCHwIFATlkZAIID2QWDmYPDxYCHwIFBTJOTTYxZGQCAQ8PFgIfAgUURlVOREFNRU5UT1MgREUgSS4gQS5kZAICDw8WAh8CBQIxMGRkAgMPDxYCHwIFAjEwZGQCBA8PFgIfAgUCMTBkZAIFDw8WAh8CBQEtZGQCBg8PFgIfAgUCMTBkZAIJD2QWDmYPDxYCHwIFBTNOVjcxZGQCAQ8PFgIfAgUbSU5HRU5JRVJJQSBERUwgQ09OT0NJTUlFTlRPZGQCAg8PFgIfAgUBOWRkAgMPDxYCHwIFATlkZAIEDw8WAh8CBQE5ZGQCBQ8PFgIfAgUBLWRkAgYPDxYCHwIFATlkZAIKD2QWDmYPDxYCHwIFBTNOTTYxZGQCAQ8PFgIfAgUVU0VHVVJJREFEIElORk9STUFUSUNBZGQCAg8PFgIfAgUBOWRkAgMPDxYCHwIFAjEwZGQCBA8PFgIfAgUBOWRkAgUPDxYCHwIFAS1kZAIGDw8WAh8CBQE5ZGQCCw9kFg5mDw8WAh8CBQUyTk03MWRkAgEPDxYCHwIFCEVMRUNUSVZBZGQCAg8PFgIfAgUBLWRkAgMPDxYCHwIFAS1kZAIEDw8WAh8CBQJBQ2RkAgUPDxYCHwIFAS1kZAIGDw8WAh8CBQJBQ2RkAgwPDxYCHwxoZGQCCQ88KwANAgAPFgIfAWdkDBQrAAwFLDA6MCwwOjEsMDoyLDA6MywwOjQsMDo1LDA6NiwwOjcsMDo4LDA6OSwwOjEwFCsAAhYQHwIFEERhdG9zIFBlcnNvbmFsZXMfAwUQRGF0b3MgUGVyc29uYWxlcx8EBSIvQWx1bW5vcy9pbmZvX2FsdW1ub3MvZGVmYXVsdC5hc3B4HwUFD0RhdG9zIEdlbmVyYWxlcx8GZx8HZx8IBSIvYWx1bW5vcy9pbmZvX2FsdW1ub3MvZGVmYXVsdC5hc3B4HwlnFCsABAULMDowLDA6MSwwOjIUKwACFhAfAgUQRGF0b3MgUGVyc29uYWxlcx8DBRBEYXRvcyBQZXJzb25hbGVzHwQFJy9BbHVtbm9zL2luZm9fYWx1bW5vcy9EYXRvc19BbHVtbm8uYXNweB8FBRVJbmZvcm1hY2nDs24gUGVyc29uYWwfBmcfB2cfCAUnL2FsdW1ub3MvaW5mb19hbHVtbm9zL2RhdG9zX2FsdW1uby5hc3B4HwlnZBQrAAIWEB8CBQ5EYXRvcyBNw6lkaWNvcx8DBQ5EYXRvcyBNw6lkaWNvcx8EBS4vQWx1bW5vcy9pbmZvX2FsdW1ub3MvRGF0b3NBbHVtbm9zTWVkaWNvcy5hc3B4HwUFFUluZm9ybWFjacOzbiBQZXJzb25hbB8GZx8HZx8IBS4vYWx1bW5vcy9pbmZvX2FsdW1ub3MvZGF0b3NhbHVtbm9zbWVkaWNvcy5hc3B4HwlnZBQrAAIWEB8CBRBEYXRvcyBEZXBvcnRpdm9zHwMFEERhdG9zIERlcG9ydGl2b3MfBAUxL0FsdW1ub3MvaW5mb19hbHVtbm9zL0RhdG9zQWx1bW5vc0RlcG9ydGl2b3MuYXNweB8FBRVJbmZvcm1hY2nDs24gUGVyc29uYWwfBmcfB2cfCAUxL2FsdW1ub3MvaW5mb19hbHVtbm9zL2RhdG9zYWx1bW5vc2RlcG9ydGl2b3MuYXNweB8JZ2QUKwACFhAfAgURRGF0b3MgYWNhZMOpbWljb3MfAwURRGF0b3MgYWNhZMOpbWljb3MfBAUcL0FsdW1ub3MvYm9sZXRhL2RlZmF1bHQuYXNweB8FBSJNdWVzdHJhIHR1IGluZm9ybWFjacOzbiBhY2Fkw6ltaWNhHwZnHwdnHwgFHC9hbHVtbm9zL2JvbGV0YS9kZWZhdWx0LmFzcHgfCWcUKwAFBQ8wOjAsMDoxLDA6MiwwOjMUKwACFhAfAgUHS8OhcmRleB8DBQdLw6FyZGV4HwQFGy9BbHVtbm9zL2JvbGV0YS9rYXJkZXguYXNweB8FBRRIaXN0b3JpYWwgQWNhZMOpbWljbx8GZx8HZx8IBRsvYWx1bW5vcy9ib2xldGEva2FyZGV4LmFzcHgfCWdkFCsAAhYQHwIFDkVzdGFkbyBHZW5lcmFsHwMFDkVzdGFkbyBHZW5lcmFsHwQFIi9BbHVtbm9zL2JvbGV0YS9Fc3RhZG9fQWx1bW5vLmFzcHgfBQUXSW5kaWNhZG9yZXMgYWNhZMOpbWljb3MfBmcfB2cfCAUiL2FsdW1ub3MvYm9sZXRhL2VzdGFkb19hbHVtbm8uYXNweB8JZ2QUKwACFhAfAgUSU29saWNpdHVkIERpY3RhbWVuHwMFElNvbGljaXR1ZCBEaWN0YW1lbh8EBSIvQWx1bW5vcy9EaWN0YW1lbmVzL0NhbmRpZGF0by5hc3B4HwUFD0RhdG9zIGdlbmVyYWxlcx8GZx8HZx8IBSIvYWx1bW5vcy9kaWN0YW1lbmVzL2NhbmRpZGF0by5hc3B4HwlnZBQrAAIWEB8CBQhEaWN0YW1lbh8DBQhEaWN0YW1lbh8EBSsvQWx1bW5vcy9EaWN0YW1lbmVzL3Jlc3B1ZXN0YV9kaWN0YW1lbi5hc3B4HwUFFUluZm9ybWFjacOzbiBQZXJzb25hbB8GZx8HZx8IBSsvYWx1bW5vcy9kaWN0YW1lbmVzL3Jlc3B1ZXN0YV9kaWN0YW1lbi5hc3B4HwlnZBQrAAIWEB8CBRNJbnNjcmlwY2nDs24gQWN0dWFsHwMFE0luc2NyaXBjacOzbiBBY3R1YWwfBAUrL0FsdW1ub3MvSW5mb3JtYWNpb25fc2VtZXN0cmFsL2RlZmF1bHQuYXNweB8FBShJbmZvcm1hY2lvbiBkZWwgcGVyaW9kbyBlc2NvbGFyIGVuIGN1cnNvHwZnHwdnHwgFKy9hbHVtbm9zL2luZm9ybWFjaW9uX3NlbWVzdHJhbC9kZWZhdWx0LmFzcHgfCWcUKwADBQcwOjAsMDoxFCsAAhYQHwIFB0hvcmFyaW8fAwUHSG9yYXJpbx8EBTIvQWx1bW5vcy9JbmZvcm1hY2lvbl9zZW1lc3RyYWwvSG9yYXJpb19BbHVtbm8uYXNweB8FBRFIb3JhcmlvIGRlIENsYXNlcx8GZx8HZx8IBTIvYWx1bW5vcy9pbmZvcm1hY2lvbl9zZW1lc3RyYWwvaG9yYXJpb19hbHVtbm8uYXNweB8JZ2QUKwACFhIfAwUOQ2FsaWZpY2FjaW9uZXMfCWcfCmcfAgUOQ2FsaWZpY2FjaW9uZXMfBAU2L0FsdW1ub3MvSW5mb3JtYWNpb25fc2VtZXN0cmFsL2NhbGlmaWNhY2lvbmVzX3NlbS5hc3B4HwZnHwdnHwUFJUNhbGlmaWNhY2lvbmVzIGRlIGxvcyBkZXBhcnRhbWVudGFsZXMfCAU2L2FsdW1ub3MvaW5mb3JtYWNpb25fc2VtZXN0cmFsL2NhbGlmaWNhY2lvbmVzX3NlbS5hc3B4ZBQrAAIWEB8CBQ9SZWluc2NyaXBjaW9uZXMfAwUPUmVpbnNjcmlwY2lvbmVzHwQFJS9BbHVtbm9zL1JlaW5zY3JpcGNpb25lcy9kZWZhdWx0LmFzcHgfBQUPUmVpbnNjcmlwY2lvbmVzHwZnHwdnHwgFJS9hbHVtbm9zL3JlaW5zY3JpcGNpb25lcy9kZWZhdWx0LmFzcHgfCWcUKwAEBQswOjAsMDoxLDA6MhQrAAIWEB8CBRVDaXRhIGRlIFJlaW5zY3JpcGNpb24fAwUVQ2l0YSBkZSBSZWluc2NyaXBjaW9uHwQFMi9BbHVtbm9zL1JlaW5zY3JpcGNpb25lcy9maWNoYXNfcmVpbnNjcmlwY2lvbi5hc3B4HwUFFUNpdGEgZGUgUmVpbnNjcmlwY2lvbh8GZx8HZx8IBTIvYWx1bW5vcy9yZWluc2NyaXBjaW9uZXMvZmljaGFzX3JlaW5zY3JpcGNpb24uYXNweB8JZ2QUKwACFhAfAgUNUmVpbnNjcmlwY2lvbh8DBQ1SZWluc2NyaXBjaW9uHwQFKS9BbHVtbm9zL1JlaW5zY3JpcGNpb25lcy9yZWluc2NyaWJpci5hc3B4HwUFDVJlaW5zY3JpcGNpb24fBmcfB2cfCAUpL2FsdW1ub3MvcmVpbnNjcmlwY2lvbmVzL3JlaW5zY3JpYmlyLmFzcHgfCWdkFCsAAhYQHwIFC0NvbXByb2JhbnRlHwMFC0NvbXByb2JhbnRlHwQFMS9BbHVtbm9zL1JlaW5zY3JpcGNpb25lcy9Db21wcm9iYW50ZV9Ib3JhcmlvLmFzcHgfBQULQ29tcHJvYmFudGUfBmcfB2cfCAUxL2FsdW1ub3MvcmVpbnNjcmlwY2lvbmVzL2NvbXByb2JhbnRlX2hvcmFyaW8uYXNweB8JZ2QUKwACFhAfAgUDRVRTHwMFA0VUUx8EBRkvQWx1bW5vcy9FVFMvZGVmYXVsdC5hc3B4HwUFBUUuVC5THwZnHwdnHwgFGS9hbHVtbm9zL2V0cy9kZWZhdWx0LmFzcHgfCWcUKwADBQcwOjAsMDoxFCsAAhYQHwIFDUluc2NyaWJpciBFVFMfAwUNSW5zY3JpYmlyIEVUUx8EBSEvQWx1bW5vcy9FVFMvaW5zY3JpcGNpb25fZXRzLmFzcHgfBQUNSW5zY3JpYmlyIEVUUx8GZx8HZx8IBSEvYWx1bW5vcy9ldHMvaW5zY3JpcGNpb25fZXRzLmFzcHgfCWdkFCsAAhYQHwIFDkNhbGlmaWNhY2lvbmVzHwMFDkNhbGlmaWNhY2lvbmVzHwQFJC9BbHVtbm9zL0VUUy9jYWxpZmljYWNpb25lc19ldHMuYXNweB8FBRBDYWxpZmljYWNpb24gRVRTHwZnHwdnHwgFJC9hbHVtbm9zL2V0cy9jYWxpZmljYWNpb25lc19ldHMuYXNweB8JZ2QUKwACFhAfAgUeU2FiZXJlcyBQcmV2aWFtZW50ZSBBZHF1aXJpZG9zHwMFHlNhYmVyZXMgUHJldmlhbWVudGUgQWRxdWlyaWRvcx8EBR0vQWx1bW5vcy9TYWJlcmVzL0RFRkFVTFQuQVNQWB8FBR5TYWJlcmVzIFByZXZpYW1lbnRlIEFkcXVpcmlkb3MfBmcfB2cfCAUdL2FsdW1ub3Mvc2FiZXJlcy9kZWZhdWx0LmFzcHgfCWcUKwADBQcwOjAsMDoxFCsAAhYQHwIFKEluc2NyaWJpciBTYWJlcmVzIFByZXZpYW1lbnRlIEFkcXVpcmlkb3MfAwUoSW5zY3JpYmlyIFNhYmVyZXMgUHJldmlhbWVudGUgQWRxdWlyaWRvcx8EBSkvQWx1bW5vcy9TYWJlcmVzL0luc2NyaXBjaW9uX1NhYmVyZXMuYXNweB8FBShJbnNjcmliaXIgU2FiZXJlcyBQcmV2aWFtZW50ZSBBZHF1aXJpZG9zHwZnHwdnHwgFKS9hbHVtbm9zL3NhYmVyZXMvaW5zY3JpcGNpb25fc2FiZXJlcy5hc3B4HwlnZBQrAAIWEB8CBQ5DYWxpZmljYWNpb25lcx8DBQ5DYWxpZmljYWNpb25lcx8EBSwvQWx1bW5vcy9TYWJlcmVzL2NhbGlmaWNhY2lvbmVzX3NhYmVyZXMuYXNweB8FBS1DYWxpZmljYWNpb25lcyBTYWJlcmVzIFByZXZpYW1lbnRlIEFkcXVpcmlkb3MfBmcfB2cfCAUsL2FsdW1ub3Mvc2FiZXJlcy9jYWxpZmljYWNpb25lc19zYWJlcmVzLmFzcHgfCWdkFCsAAhYQHwIFFkN1cnNvcyBkZSBSZWN1cGVyYWNpb24fAwUWQ3Vyc29zIGRlIFJlY3VwZXJhY2lvbh8EBSIvQWx1bW5vcy9SZWN1cGVyYWNpb24vREVGQVVMVC5BU1BYHwUFFkN1cnNvcyBkZSBSZWN1cGVyYWNpb24fBmcfB2cfCAUiL2FsdW1ub3MvcmVjdXBlcmFjaW9uL2RlZmF1bHQuYXNweB8JZxQrAAIFAzA6MBQrAAIWEB8CBSBJbnNjcmliaXIgQ3Vyc29zIGRlIFJlY3VwZXJhY2lvbh8DBSBJbnNjcmliaXIgQ3Vyc29zIGRlIFJlY3VwZXJhY2lvbh8EBTMvQWx1bW5vcy9SZWN1cGVyYWNpb24vSW5zY3JpcGNpb25fUmVjdXBlcmFjaW9uLmFzcHgfBQUgSW5zY3JpYmlyIEN1cnNvcyBkZSBSZWN1cGVyYWNpb24fBmcfB2cfCAUzL2FsdW1ub3MvcmVjdXBlcmFjaW9uL2luc2NyaXBjaW9uX3JlY3VwZXJhY2lvbi5hc3B4HwlnZBQrAAIWEB8CBQdUdXRvcmVzHwMFB1R1dG9yZXMfBAUdL0FsdW1ub3MvVHV0b3Jlcy9EZWZhdWx0LmFzcHgfBQUQRGF0b3MgZGUgVHV0b3JpYR8GZx8HZx8IBR0vYWx1bW5vcy90dXRvcmVzL2RlZmF1bHQuYXNweB8JZxQrAAQFCzA6MCwwOjEsMDoyFCsAAhYQHwIFDFR1dG9yIEFjdHVhbB8DBQxUdXRvciBBY3R1YWwfBAUhL0FsdW1ub3MvdHV0b3Jlcy9EYXRvc19UdXRvci5hc3B4HwUFH0RhdG9zIGRlbCBQcm9mZXNvciBUdXRvciBBY3R1YWwfBmcfB2cfCAUhL2FsdW1ub3MvdHV0b3Jlcy9kYXRvc190dXRvci5hc3B4HwlnZBQrAAIWEB8CBQtDb21lbnRhcmlvcx8DBQtDb21lbnRhcmlvcx8EBSEvQWx1bW5vcy90dXRvcmVzL2NvbWVudGFyaW9zLmFzcHgfBQUuQ29tZW50YXJpb3MgcmVhbGl6YWRvcyBwb3IgZWwgdHV0b3IgZGVsIGFsdW1ubx8GZx8HZx8IBSEvYWx1bW5vcy90dXRvcmVzL2NvbWVudGFyaW9zLmFzcHgfCWdkFCsAAhYQHwIFC0V2YWx1YWNpw7NuHwMFC0V2YWx1YWNpw7NuHwQFKC9BbHVtbm9zL3R1dG9yZXMvRXZhbHVhY2lvbl9UdXRvcmVzLmFzcHgfBQUdRXZhbHVhY2nDs24gYWwgcHJvZmVzb3IgdHV0b3IfBmcfB2cfCAUoL2FsdW1ub3MvdHV0b3Jlcy9ldmFsdWFjaW9uX3R1dG9yZXMuYXNweB8JZ2QUKwACFhAfAgUTRXZhbHVhY2lvbiBQcm9mZXNvch8DBRNFdmFsdWFjaW9uIFByb2Zlc29yHwQFKC9BbHVtbm9zL0V2YWx1YWNpb25fRG9jZW50ZS9EZWZhdWx0LmFzcHgfBQUTRGF0b3MgZGUgRXZhbHVhY2lvbh8GZx8HZx8IBSgvYWx1bW5vcy9ldmFsdWFjaW9uX2RvY2VudGUvZGVmYXVsdC5hc3B4HwlnFCsAAgUDMDowFCsAAhYQHwIFC0V2YWx1YWNpw7NuHwMFC0V2YWx1YWNpw7NuHwQFLy9BbHVtbm9zL0V2YWx1YWNpb25fZG9jZW50ZS9jYWxpZmljYV9wcm9mZS5hc3B4HwUFFUV2YWx1YWNpb24gUHJvZmVzb3Jlcx8GZx8HZx8IBS8vYWx1bW5vcy9ldmFsdWFjaW9uX2RvY2VudGUvY2FsaWZpY2FfcHJvZmUuYXNweB8JZ2QUKwACFhAfAgUPQ29ycmVvIHBlcnNvbmFsHwMFD0NvcnJlbyBwZXJzb25hbB8EBSIvQWx1bW5vcy9DYW1iaW9Db3JyZW9QZXJzb25hbC5hc3B4HwUFGUNhbWJpbyBkZSBjb3JyZW8gcGVyc29uYWwfBmcfB2cfCAUiL2FsdW1ub3MvY2FtYmlvY29ycmVvcGVyc29uYWwuYXNweB8JZ2QUKwACFhAfAgUPQ2xhdmUgZGUgYWNjZXNvHwMFD0NsYXZlIGRlIGFjY2Vzbx8EBRovQWx1bW5vcy9jYW1iaWFfY2xhdmUuYXNweB8FBR9DYW1iaW8gZGUgQ29udHJhc2XDsWEgZGUgYWNjZXNvHwZnHwdnHwgFGi9hbHVtbm9zL2NhbWJpYV9jbGF2ZS5hc3B4HwlnZGQCCg9kFgICAw8WAh8MaGQYBAUeX19Db250cm9sc1JlcXVpcmVQb3N0QmFja0tleV9fFgIFKWN0bDAwJGxlZnRDb2x1bW4kTG9naW5TdGF0dXNTZXNzaW9uJGN0bDAxBSljdGwwMCRsZWZ0Q29sdW1uJExvZ2luU3RhdHVzU2Vzc2lvbiRjdGwwMwUNY3RsMDAkc3ViTWVudQ8PZAUiSW5zY3JpcGNpw7NuIEFjdHVhbFxDYWxpZmljYWNpb25lc2QFF2N0bDAwJG1haW5Db3B5JEdWX0NhbGlmDzwrAAoBCAIBZAUOY3RsMDAkbWFpbm1lbnUPD2QFDkluaWNpb1xBbHVtbm9zZPsb6uyr9Gmp/e7Sa/JLcyCriP/b\">\n" +
                "</div>\n" +
                "\n" +
                "<script type=\"text/javascript\">\n" +
                "//<![CDATA[\n" +
                "var theForm = document.forms['aspnetForm'];\n" +
                "if (!theForm) {\n" +
                "    theForm = document.aspnetForm;\n" +
                "}\n" +
                "function __doPostBack(eventTarget, eventArgument) {\n" +
                "    if (!theForm.onsubmit || (theForm.onsubmit() != false)) {\n" +
                "        theForm.__EVENTTARGET.value = eventTarget;\n" +
                "        theForm.__EVENTARGUMENT.value = eventArgument;\n" +
                "        theForm.submit();\n" +
                "    }\n" +
                "}\n" +
                "//]]>\n" +
                "</script>\n" +
                "\n" +
                "\n" +
                "<script src=\"./Calificaciones del semestre SAES_files/WebResource.axd\" type=\"text/javascript\"></script>\n" +
                "\n" +
                "\n" +
                "<script src=\"./Calificaciones del semestre SAES_files/WebResource(1).axd\" type=\"text/javascript\"></script>\n" +
                "<div>\n" +
                "\n" +
                "\t<input type=\"hidden\" name=\"__VIEWSTATEGENERATOR\" id=\"__VIEWSTATEGENERATOR\" value=\"923474A7\">\n" +
                "\t<input type=\"hidden\" name=\"__EVENTVALIDATION\" id=\"__EVENTVALIDATION\" value=\"/wEWAgKu4LeyDwLui87GCB823Vrt8GkicSywFZPNZEB/yKqR\">\n" +
                "</div>\n" +
                "\t\t<div id=\"wrapper\" style=\"display: table;\">\n" +
                "\t\t\t<div id=\"header\">\n" +
                "\t\t\t\n" +
                "\t\t\t\t<div id=\"logo\">\n" +
                "\t\t\t\t\t<a id=\"ctl00_lnkHome\" title=\"Página principal\" href=\"https://www.saes.upiicsa.ipn.mx/Default.aspx\" style=\"display:inline-block;height:75%;width:75%;\"><img title=\"Página principal\" src=\"./Calificaciones del semestre SAES_files/Poli_XCH.png\" alt=\"[Home]\" style=\"border-width:0px;\"></a>\n" +
                "\t\t\t\t</div>\n" +
                "\t\t\t\t<div id=\"banner\" style=\"font-weight: bold; font-size: small; color: maroon; font-family: Tahoma; font-variant: small-caps; right: 139px; top: 3px; text-align: center; width: 720px;\">\n" +
                "\t\t\t\t\t\n" +
                "\t\t\t\t\t<br>\n" +
                "\t\t\t\t\tUNIDAD PROFESIONAL INTERDISCIPLINARIA DE INGENIERÍA Y\t\t\t\n" +
                "                    <br>\n" +
                "                    CIENCIAS SOCIALES Y ADMINISTRATIVAS\n" +
                "\t\t\t\t</div>\n" +
                "\t\t\t\t<div id=\"search\">\n" +
                "                   <table>\n" +
                "                   <tbody><tr>\n" +
                "                       <td rowspan=\"2\">\n" +
                "                       <img id=\"ctl00_LogoEscuela\" src=\"./Calificaciones del semestre SAES_files/60.png\" alt=\"Escuela\" style=\"height:60px;width:60px;border-width:0px;\">\n" +
                "                       </td>\n" +
                "                   <td>\n" +
                "                        <a id=\"ctl00_Correo\" href=\"https://www.saes.upiicsa.ipn.mx/#\"><img src=\"./Calificaciones del semestre SAES_files/email.png\" alt=\"Email\" style=\"border-width:0px;\"></a>&nbsp;\n" +
                "                    </td>\n" +
                "                   </tr>\n" +
                "                   <tr>\n" +
                "                   <td>\n" +
                "                        <a id=\"ctl00_Mapa\" href=\"https://www.saes.upiicsa.ipn.mx/Mapa.aspx\"><img src=\"./Calificaciones del semestre SAES_files/mapa.png\" alt=\"Mapa\" style=\"border-width:0px;\"></a>&nbsp;</td>\n" +
                "                   </tr>\n" +
                "                   </tbody></table>\n" +
                "                    </div>\n" +
                "\t\t\t</div>\n" +
                "\t\t\t<div id=\"mainnav\" style=\"padding: 0 254px; border-bottom: 1px solid #FFF; border-top: 1px solid #990000; background-color: #F2F2F2;\">\n" +
                "\t\t\t\t\n" +
                "\t\t\t\t<a href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/Informacion_semestral/calificaciones_sem.aspx#ctl00_mainmenu_SkipLink\"><img alt=\"Omitir vínculos de navegación\" src=\"./Calificaciones del semestre SAES_files/WebResource(2).axd\" width=\"0\" height=\"0\" style=\"border-width:0px;\"></a><table id=\"ctl00_mainmenu\" class=\"ctl00_mainmenu_5 ctl00_mainmenu_2\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n" +
                "\t<tbody><tr>\n" +
                "\t\t<td onmouseover=\"Menu_HoverRoot(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Inicio\" id=\"ctl00_mainmenun0\"><table class=\"item ctl00_mainmenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">\n" +
                "\t\t\t<tbody><tr>\n" +
                "\t\t\t\t<td style=\"white-space:nowrap;\"><a class=\"ctl00_mainmenu_1 item ctl00_mainmenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/default.aspx\" style=\"border-style:none;font-size:1em;\">Inicio</a></td>\n" +
                "\t\t\t</tr>\n" +
                "\t\t</tbody></table></td><td style=\"width:0px;\"></td><td><table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" class=\"ctl00_mainmenu_5\">\n" +
                "\t\t\t<tbody><tr>\n" +
                "\t\t\t\t<td style=\"width:0px;\"></td><td onmouseover=\"Menu_HoverStatic(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Alumnos\" id=\"ctl00_mainmenun1\"><table class=\"item ctl00_mainmenu_4 selected ctl00_mainmenu_7\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">\n" +
                "\t\t\t\t\t<tbody><tr>\n" +
                "\t\t\t\t\t\t<td style=\"white-space:nowrap;\"><a class=\"ctl00_mainmenu_1 item ctl00_mainmenu_3 selected ctl00_mainmenu_6\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/default.aspx\" style=\"border-style:none;font-size:1em;\">Alumnos</a></td>\n" +
                "\t\t\t\t\t</tr>\n" +
                "\t\t\t\t</tbody></table></td><td style=\"width:0px;\"></td><td style=\"width:0px;\"></td><td onmouseover=\"Menu_HoverStatic(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Académica\" id=\"ctl00_mainmenun2\"><table class=\"item ctl00_mainmenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">\n" +
                "\t\t\t\t\t<tbody><tr>\n" +
                "\t\t\t\t\t\t<td style=\"white-space:nowrap;\"><a class=\"ctl00_mainmenu_1 item ctl00_mainmenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Academica/default.aspx\" style=\"border-style:none;font-size:1em;\">Académica</a></td>\n" +
                "\t\t\t\t\t</tr>\n" +
                "\t\t\t\t</tbody></table></td><td style=\"width:0px;\"></td><td style=\"width:0px;\"></td><td onmouseover=\"Menu_HoverStatic(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Reglamento\" id=\"ctl00_mainmenun3\"><table class=\"item ctl00_mainmenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">\n" +
                "\t\t\t\t\t<tbody><tr>\n" +
                "\t\t\t\t\t\t<td style=\"white-space:nowrap;\"><a class=\"ctl00_mainmenu_1 item ctl00_mainmenu_3\" href=\"http://www.contenido.ccs.ipn.mx/G-866-2011-E.pdf\" style=\"border-style:none;font-size:1em;\" target=\"_blank\">Reglamento</a></td>\n" +
                "\t\t\t\t\t</tr>\n" +
                "\t\t\t\t</tbody></table></td><td style=\"width:0px;\"></td><td style=\"width:0px;\"></td><td onmouseover=\"Menu_HoverStatic(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Ayuda\" id=\"ctl00_mainmenun4\"><table class=\"item ctl00_mainmenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">\n" +
                "\t\t\t\t\t<tbody><tr>\n" +
                "\t\t\t\t\t\t<td style=\"white-space:nowrap;\"><a class=\"ctl00_mainmenu_1 item ctl00_mainmenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Ayuda/Ayuda.aspx\" style=\"border-style:none;font-size:1em;\">Ayuda</a></td>\n" +
                "\t\t\t\t\t</tr>\n" +
                "\t\t\t\t</tbody></table></td><td style=\"width:0px;\"></td><td style=\"width:0px;\"></td><td onmouseover=\"Menu_HoverStatic(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Recuperar la Contraseña\" id=\"ctl00_mainmenun5\"><table class=\"item ctl00_mainmenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">\n" +
                "\t\t\t\t\t<tbody><tr>\n" +
                "\t\t\t\t\t\t<td style=\"white-space:nowrap;\"><a class=\"ctl00_mainmenu_1 item ctl00_mainmenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/SendEmail/PruebaSendMail.aspx\" style=\"border-style:none;font-size:1em;\">Recuperar Contraseña</a></td>\n" +
                "\t\t\t\t\t</tr>\n" +
                "\t\t\t\t</tbody></table></td><td style=\"width:0px;\"></td>\n" +
                "\t\t\t</tr>\n" +
                "\t\t</tbody></table></td>\n" +
                "\t</tr>\n" +
                "</tbody></table><a id=\"ctl00_mainmenu_SkipLink\"></a>\t\t\t\n" +
                "\t\t\t</div>\n" +
                "\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\n" +
                "\t\t\t<div id=\"contentwrapper\" style=\"margin-left: 0px; display: table;\">\n" +
                "\t\t\t\t<div id=\"leftcolumn\" style=\"display: table-cell; vertical-align: top;\">\n" +
                "\t\t\t\t\t\t<div id=\"leftcolcontainer\" style=\" width : initial; \">\n" +
                "\t\t\t\t\t\t\t<div id=\"subnav\">\t&nbsp;\n" +
                "\t\t\t\t\t\t\t\t<a href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/Informacion_semestral/calificaciones_sem.aspx#ctl00_subMenu_SkipLink\"><img alt=\"Omitir vínculos de navegación\" src=\"./Calificaciones del semestre SAES_files/WebResource(2).axd\" width=\"0\" height=\"0\" style=\"border-width:0px;\"></a><table id=\"ctl00_subMenu\" class=\"ctl00_subMenu_5 ctl00_subMenu_2\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"width: auto;\">\n" +
                "\t<tbody><tr onmouseover=\"Menu_HoverRoot(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Datos Generales\" id=\"ctl00_subMenun0\">\n" +
                "\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"width: 100%;\">\n" +
                "\t\t\t<tbody><tr>\n" +
                "\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/info_alumnos/default.aspx\" style=\"border-style:none;font-size:1em;\">Datos Personales</a></td>\n" +
                "\t\t\t</tr>\n" +
                "\t\t</tbody></table></td>\n" +
                "\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t<td></td>\n" +
                "\t</tr><tr>\n" +
                "\t\t<td><table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" class=\"ctl00_subMenu_5\" style=\"width: 100%;\">\n" +
                "\t\t\t<tbody><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr onmouseover=\"Menu_HoverStatic(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Información Personal\" id=\"ctl00_subMenun1\">\n" +
                "\t\t\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">\n" +
                "\t\t\t\t\t<tbody><tr>\n" +
                "\t\t\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/info_alumnos/Datos_Alumno.aspx\" style=\"border-style:none;font-size:1em;margin-left:20px;\">General</a></td>\n" +
                "\t\t\t\t\t</tr>\n" +
                "\t\t\t\t</tbody></table></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr onmouseover=\"Menu_HoverStatic(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Información Personal\" id=\"ctl00_subMenun2\">\n" +
                "\t\t\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">\n" +
                "\t\t\t\t\t<tbody><tr>\n" +
                "\t\t\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/info_alumnos/DatosAlumnosMedicos.aspx\" style=\"border-style:none;font-size:1em;margin-left:20px;\">Médicos</a></td>\n" +
                "\t\t\t\t\t</tr>\n" +
                "\t\t\t\t</tbody></table></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr onmouseover=\"Menu_HoverStatic(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Información Personal\" id=\"ctl00_subMenun3\">\n" +
                "\t\t\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">\n" +
                "\t\t\t\t\t<tbody><tr>\n" +
                "\t\t\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/info_alumnos/DatosAlumnosDeportivos.aspx\" style=\"border-style:none;font-size:1em;margin-left:20px;\">Deportivos</a></td>\n" +
                "\t\t\t\t\t</tr>\n" +
                "\t\t\t\t</tbody></table></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr>\n" +
                "\t\t</tbody></table></td>\n" +
                "\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t<td></td>\n" +
                "\t</tr><tr onmouseover=\"Menu_HoverRoot(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Muestra tu información académica\" id=\"ctl00_subMenun4\">\n" +
                "\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"width: 100%;\">\n" +
                "\t\t\t<tbody><tr>\n" +
                "\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/boleta/default.aspx\" style=\"border-style:none;font-size:1em;\">Datos académicos</a></td>\n" +
                "\t\t\t</tr>\n" +
                "\t\t</tbody></table></td>\n" +
                "\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t<td></td>\n" +
                "\t</tr><tr>\n" +
                "\t\t<td><table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" class=\"ctl00_subMenu_5\" style=\"width: 100%;\">\n" +
                "\t\t\t<tbody><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr onmouseover=\"Menu_HoverStatic(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Historial Académico\" id=\"ctl00_subMenun5\">\n" +
                "\t\t\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">\n" +
                "\t\t\t\t\t<tbody><tr>\n" +
                "\t\t\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/boleta/kardex.aspx\" style=\"border-style:none;font-size:1em;margin-left:20px;\">Kárdex</a></td>\n" +
                "\t\t\t\t\t</tr>\n" +
                "\t\t\t\t</tbody></table></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr onmouseover=\"Menu_HoverStatic(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Indicadores académicos\" id=\"ctl00_subMenun6\">\n" +
                "\t\t\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">\n" +
                "\t\t\t\t\t<tbody><tr>\n" +
                "\t\t\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/boleta/Estado_Alumno.aspx\" style=\"border-style:none;font-size:1em;margin-left:20px;\">Estado General</a></td>\n" +
                "\t\t\t\t\t</tr>\n" +
                "\t\t\t\t</tbody></table></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr onmouseover=\"Menu_HoverStatic(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Datos generales\" id=\"ctl00_subMenun7\">\n" +
                "\t\t\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">\n" +
                "\t\t\t\t\t<tbody><tr>\n" +
                "\t\t\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/Dictamenes/Candidato.aspx\" style=\"border-style:none;font-size:1em;margin-left:20px;\">Solicitud Dictamen</a></td>\n" +
                "\t\t\t\t\t</tr>\n" +
                "\t\t\t\t</tbody></table></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr onmouseover=\"Menu_HoverStatic(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Información Personal\" id=\"ctl00_subMenun8\">\n" +
                "\t\t\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">\n" +
                "\t\t\t\t\t<tbody><tr>\n" +
                "\t\t\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/Dictamenes/respuesta_dictamen.aspx\" style=\"border-style:none;font-size:1em;margin-left:20px;\">Dictamen</a></td>\n" +
                "\t\t\t\t\t</tr>\n" +
                "\t\t\t\t</tbody></table></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr>\n" +
                "\t\t</tbody></table></td>\n" +
                "\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t<td></td>\n" +
                "\t</tr><tr onmouseover=\"Menu_HoverRoot(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Informacion del periodo escolar en curso\" id=\"ctl00_subMenun9\">\n" +
                "\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"width: 100%;\">\n" +
                "\t\t\t<tbody><tr>\n" +
                "\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/Informacion_semestral/default.aspx\" style=\"border-style:none;font-size:1em;\">Inscripción Actual</a></td>\n" +
                "\t\t\t</tr>\n" +
                "\t\t</tbody></table></td>\n" +
                "\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t<td></td>\n" +
                "\t</tr><tr>\n" +
                "\t\t<td><table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" class=\"ctl00_subMenu_5\" style=\"width: 100%;\">\n" +
                "\t\t\t<tbody><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr onmouseover=\"Menu_HoverStatic(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Horario de Clases\" id=\"ctl00_subMenun10\">\n" +
                "\t\t\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">\n" +
                "\t\t\t\t\t<tbody><tr>\n" +
                "\t\t\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/Informacion_semestral/Horario_Alumno.aspx\" style=\"border-style:none;font-size:1em;margin-left:20px;\">Horario</a></td>\n" +
                "\t\t\t\t\t</tr>\n" +
                "\t\t\t\t</tbody></table></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr onmouseover=\"Menu_HoverStatic(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Calificaciones de los departamentales\" id=\"ctl00_subMenun11\">\n" +
                "\t\t\t\t<td><table class=\"item ctl00_subMenu_4 selected ctl00_subMenu_10\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">\n" +
                "\t\t\t\t\t<tbody><tr>\n" +
                "\t\t\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3 selected ctl00_subMenu_9\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/Informacion_semestral/calificaciones_sem.aspx\" style=\"border-style:none;font-size:1em;margin-left:20px;\">Calificaciones</a></td>\n" +
                "\t\t\t\t\t</tr>\n" +
                "\t\t\t\t</tbody></table></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr>\n" +
                "\t\t</tbody></table></td>\n" +
                "\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t<td></td>\n" +
                "\t</tr><tr onmouseover=\"Menu_HoverRoot(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Reinscripciones\" id=\"ctl00_subMenun12\">\n" +
                "\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"width: 100%;\">\n" +
                "\t\t\t<tbody><tr>\n" +
                "\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/Reinscripciones/default.aspx\" style=\"border-style:none;font-size:1em;\">Reinscripciones</a></td>\n" +
                "\t\t\t</tr>\n" +
                "\t\t</tbody></table></td>\n" +
                "\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t<td></td>\n" +
                "\t</tr><tr>\n" +
                "\t\t<td><table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" class=\"ctl00_subMenu_5\" style=\"width: 100%;\">\n" +
                "\t\t\t<tbody><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr onmouseover=\"Menu_HoverStatic(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Cita de Reinscripcion\" id=\"ctl00_subMenun13\">\n" +
                "\t\t\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">\n" +
                "\t\t\t\t\t<tbody><tr>\n" +
                "\t\t\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/Reinscripciones/fichas_reinscripcion.aspx\" style=\"border-style:none;font-size:1em;margin-left:20px;\">Cita de Reinscripcion</a></td>\n" +
                "\t\t\t\t\t</tr>\n" +
                "\t\t\t\t</tbody></table></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr onmouseover=\"Menu_HoverStatic(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Reinscripcion\" id=\"ctl00_subMenun14\">\n" +
                "\t\t\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">\n" +
                "\t\t\t\t\t<tbody><tr>\n" +
                "\t\t\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/Reinscripciones/reinscribir.aspx\" style=\"border-style:none;font-size:1em;margin-left:20px;\">Reinscripcion</a></td>\n" +
                "\t\t\t\t\t</tr>\n" +
                "\t\t\t\t</tbody></table></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr onmouseover=\"Menu_HoverStatic(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Comprobante\" id=\"ctl00_subMenun15\">\n" +
                "\t\t\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">\n" +
                "\t\t\t\t\t<tbody><tr>\n" +
                "\t\t\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/Reinscripciones/Comprobante_Horario.aspx\" style=\"border-style:none;font-size:1em;margin-left:20px;\">Comprobante</a></td>\n" +
                "\t\t\t\t\t</tr>\n" +
                "\t\t\t\t</tbody></table></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr>\n" +
                "\t\t</tbody></table></td>\n" +
                "\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t<td></td>\n" +
                "\t</tr><tr onmouseover=\"Menu_HoverRoot(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"E.T.S\" id=\"ctl00_subMenun16\">\n" +
                "\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"width: 100%;\">\n" +
                "\t\t\t<tbody><tr>\n" +
                "\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/Informacion_semestral/calificaciones_sem.aspx#\" style=\"border-style:none;font-size:1em;\">ETS</a></td>\n" +
                "\t\t\t</tr>\n" +
                "\t\t</tbody></table></td>\n" +
                "\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t<td></td>\n" +
                "\t</tr><tr>\n" +
                "\t\t<td><table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" class=\"ctl00_subMenu_5\" style=\"width: 100%;\">\n" +
                "\t\t\t<tbody><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr onmouseover=\"Menu_HoverStatic(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Inscribir ETS\" id=\"ctl00_subMenun17\">\n" +
                "\t\t\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">\n" +
                "\t\t\t\t\t<tbody><tr>\n" +
                "\t\t\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/ETS/inscripcion_ets.aspx\" style=\"border-style:none;font-size:1em;margin-left:20px;\">Inscribir ETS</a></td>\n" +
                "\t\t\t\t\t</tr>\n" +
                "\t\t\t\t</tbody></table></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr onmouseover=\"Menu_HoverStatic(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Calificacion ETS\" id=\"ctl00_subMenun18\">\n" +
                "\t\t\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">\n" +
                "\t\t\t\t\t<tbody><tr>\n" +
                "\t\t\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/ETS/calificaciones_ets.aspx\" style=\"border-style:none;font-size:1em;margin-left:20px;\">Calificaciones</a></td>\n" +
                "\t\t\t\t\t</tr>\n" +
                "\t\t\t\t</tbody></table></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr>\n" +
                "\t\t</tbody></table></td>\n" +
                "\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t<td></td>\n" +
                "\t</tr><tr onmouseover=\"Menu_HoverRoot(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Saberes Previamente Adquiridos\" id=\"ctl00_subMenun19\">\n" +
                "\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"width: 100%;\">\n" +
                "\t\t\t<tbody><tr>\n" +
                "\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/Saberes/DEFAULT.ASPX\" style=\"border-style:none;font-size:1em;\">SPA</a></td>\n" +
                "\t\t\t</tr>\n" +
                "\t\t</tbody></table></td>\n" +
                "\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t<td></td>\n" +
                "\t</tr><tr>\n" +
                "\t\t<td><table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" class=\"ctl00_subMenu_5\" style=\"width: 100%;\">\n" +
                "\t\t\t<tbody><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr onmouseover=\"Menu_HoverStatic(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Inscribir Saberes Previamente Adquiridos\" id=\"ctl00_subMenun20\">\n" +
                "\t\t\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">\n" +
                "\t\t\t\t\t<tbody><tr>\n" +
                "\t\t\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/Saberes/Inscripcion_Saberes.aspx\" style=\"border-style:none;font-size:1em;margin-left:20px;\">Inscribir SPA</a></td>\n" +
                "\t\t\t\t\t</tr>\n" +
                "\t\t\t\t</tbody></table></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr onmouseover=\"Menu_HoverStatic(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Calificaciones Saberes Previamente Adquiridos\" id=\"ctl00_subMenun21\">\n" +
                "\t\t\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">\n" +
                "\t\t\t\t\t<tbody><tr>\n" +
                "\t\t\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/Saberes/calificaciones_saberes.aspx\" style=\"border-style:none;font-size:1em;margin-left:20px;\">Calificaciones</a></td>\n" +
                "\t\t\t\t\t</tr>\n" +
                "\t\t\t\t</tbody></table></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr>\n" +
                "\t\t</tbody></table></td>\n" +
                "\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t<td></td>\n" +
                "\t</tr><tr onmouseover=\"Menu_HoverRoot(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Cursos de Recuperacion\" id=\"ctl00_subMenun22\">\n" +
                "\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"width: 100%;\">\n" +
                "\t\t\t<tbody><tr>\n" +
                "\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/Recuperacion/DEFAULT.ASPX\" style=\"border-style:none;font-size:1em;\">Cursos de Recuperacion</a></td>\n" +
                "\t\t\t</tr>\n" +
                "\t\t</tbody></table></td>\n" +
                "\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t<td></td>\n" +
                "\t</tr><tr>\n" +
                "\t\t<td><table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" class=\"ctl00_subMenu_5\" style=\"width: 100%;\">\n" +
                "\t\t\t<tbody><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr onmouseover=\"Menu_HoverStatic(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Inscribir Cursos de Recuperacion\" id=\"ctl00_subMenun23\">\n" +
                "\t\t\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">\n" +
                "\t\t\t\t\t<tbody><tr>\n" +
                "\t\t\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/Recuperacion/Inscripcion_Recuperacion.aspx\" style=\"border-style:none;font-size:1em;margin-left:20px;\">Inscribir Cursos de Recuperacion</a></td>\n" +
                "\t\t\t\t\t</tr>\n" +
                "\t\t\t\t</tbody></table></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr>\n" +
                "\t\t</tbody></table></td>\n" +
                "\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t<td></td>\n" +
                "\t</tr><tr onmouseover=\"Menu_HoverRoot(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Datos de Tutoria\" id=\"ctl00_subMenun24\">\n" +
                "\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"width: 100%;\">\n" +
                "\t\t\t<tbody><tr>\n" +
                "\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/Tutores/Default.aspx\" style=\"border-style:none;font-size:1em;\">Tutores</a></td>\n" +
                "\t\t\t</tr>\n" +
                "\t\t</tbody></table></td>\n" +
                "\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t<td></td>\n" +
                "\t</tr><tr>\n" +
                "\t\t<td><table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" class=\"ctl00_subMenu_5\" style=\"width: 100%;\">\n" +
                "\t\t\t<tbody><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr onmouseover=\"Menu_HoverStatic(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Datos del Profesor Tutor Actual\" id=\"ctl00_subMenun25\">\n" +
                "\t\t\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">\n" +
                "\t\t\t\t\t<tbody><tr>\n" +
                "\t\t\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/tutores/Datos_Tutor.aspx\" style=\"border-style:none;font-size:1em;margin-left:20px;\">Tutor Actual</a></td>\n" +
                "\t\t\t\t\t</tr>\n" +
                "\t\t\t\t</tbody></table></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr onmouseover=\"Menu_HoverStatic(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Comentarios realizados por el tutor del alumno\" id=\"ctl00_subMenun26\">\n" +
                "\t\t\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">\n" +
                "\t\t\t\t\t<tbody><tr>\n" +
                "\t\t\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/tutores/comentarios.aspx\" style=\"border-style:none;font-size:1em;margin-left:20px;\">Comentarios</a></td>\n" +
                "\t\t\t\t\t</tr>\n" +
                "\t\t\t\t</tbody></table></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr onmouseover=\"Menu_HoverStatic(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Evaluación al profesor tutor\" id=\"ctl00_subMenun27\">\n" +
                "\t\t\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">\n" +
                "\t\t\t\t\t<tbody><tr>\n" +
                "\t\t\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/tutores/Evaluacion_Tutores.aspx\" style=\"border-style:none;font-size:1em;margin-left:20px;\">Evaluación</a></td>\n" +
                "\t\t\t\t\t</tr>\n" +
                "\t\t\t\t</tbody></table></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr>\n" +
                "\t\t</tbody></table></td>\n" +
                "\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t<td></td>\n" +
                "\t</tr><tr onmouseover=\"Menu_HoverRoot(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Datos de Evaluacion\" id=\"ctl00_subMenun28\">\n" +
                "\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"width: 100%;\">\n" +
                "\t\t\t<tbody><tr>\n" +
                "\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/Evaluacion_Docente/Default.aspx\" style=\"border-style:none;font-size:1em;\">Profesores</a></td>\n" +
                "\t\t\t</tr>\n" +
                "\t\t</tbody></table></td>\n" +
                "\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t<td></td>\n" +
                "\t</tr><tr>\n" +
                "\t\t<td><table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" class=\"ctl00_subMenu_5\" style=\"width: 100%;\">\n" +
                "\t\t\t<tbody><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr><tr onmouseover=\"Menu_HoverStatic(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Evaluacion Profesores\" id=\"ctl00_subMenun29\">\n" +
                "\t\t\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">\n" +
                "\t\t\t\t\t<tbody><tr>\n" +
                "\t\t\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/Evaluacion_docente/califica_profe.aspx\" style=\"border-style:none;font-size:1em;margin-left:20px;\">Evaluación</a></td>\n" +
                "\t\t\t\t\t</tr>\n" +
                "\t\t\t\t</tbody></table></td>\n" +
                "\t\t\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t\t\t<td></td>\n" +
                "\t\t\t</tr>\n" +
                "\t\t</tbody></table></td>\n" +
                "\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t<td></td>\n" +
                "\t</tr><tr onmouseover=\"Menu_HoverRoot(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Cambio de correo personal\" id=\"ctl00_subMenun30\">\n" +
                "\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"width: 100%;\">\n" +
                "\t\t\t<tbody><tr>\n" +
                "\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/CambioCorreoPersonal.aspx\" style=\"border-style:none;font-size:1em;\">Correo personal</a></td>\n" +
                "\t\t\t</tr>\n" +
                "\t\t</tbody></table></td>\n" +
                "\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t<td></td>\n" +
                "\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t<td></td>\n" +
                "\t</tr><tr onmouseover=\"Menu_HoverRoot(this)\" onmouseout=\"Menu_Unhover(this)\" onkeyup=\"Menu_Key(event)\" title=\"Cambio de Contraseña de acceso\" id=\"ctl00_subMenun31\">\n" +
                "\t\t<td><table class=\"item ctl00_subMenu_4\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"width: 100%;\">\n" +
                "\t\t\t<tbody><tr>\n" +
                "\t\t\t\t<td style=\"white-space:nowrap;width:100%;\"><a class=\"ctl00_subMenu_1 item ctl00_subMenu_3\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/cambia_clave.aspx\" style=\"border-style:none;font-size:1em;\">Clave de acceso</a></td>\n" +
                "\t\t\t</tr>\n" +
                "\t\t</tbody></table></td>\n" +
                "\t</tr><tr style=\"height:0px;\">\n" +
                "\t\t<td></td>\n" +
                "\t</tr>\n" +
                "</tbody></table><style type=\"text/css\">#subnav .item { padding : 0px 7px;\tborder : none; display : block; }#subnav .item a {\twidth : 100%; display : block; }#subnav .selected:hover { background-color: #FF9900; color: #990000; }#subnav .hover { background-color: initial; color: #990000; }#subnav { margin-bottom: 0px; } .sidebarcontainer { margin : 0px auto } </style><a id=\"ctl00_subMenu_SkipLink\"></a>\n" +
                "\t\t\t\t\t\t\t</div>\n" +
                "\t\t\t\t\t\t\t\n" +
                "\t\t\t\t\t\t\t\t\n" +
                "                                <br>\n" +
                "                                <div class=\"sidebarcontainer\" style=\"width: 125px; height: 40%\">\n" +
                "                                    <div id=\"ctl00_leftColumn_LogOut\">\n" +
                "                                        <loggedintemplate>\n" +
                "                                            <a id=\"ctl00_leftColumn_LoginStatusSession\" href=\"javascript:__doPostBack(&#39;ctl00$leftColumn$LoginStatusSession$ctl00&#39;,&#39;&#39;)\" style=\"display:inline-block;width:110px;\">Cerrar sesión</a>\n" +
                "                                            <span id=\"ctl00_leftColumn_LoginNameSession\" style=\"display:inline-block;color:#FF8000;width:116px;\">2014031391</span>\n" +
                "                                        </loggedintemplate>\n" +
                "                                    </div>\n" +
                "                                    \n" +
                "                                </div>\n" +
                "\t\t\t\t\t\t\t\n" +
                "                            &nbsp;\n" +
                "\t\t\t\t\t\t</div>\n" +
                "\t\t\t\t\t</div><div id=\"floatwrapper\" style=\" display : table-cell; float : none; border-left: 1px solid #FFF; border-right: 1px solid #FFF; min-width: 648px;\">\n" +
                "\t\t\t\t\t<div id=\"centercolumn\" style=\"float : initial; width : initial; margin-left : initial; padding-top : 0px; \">\n" +
                "\t\t\t\t\t\t\n" +
                "\t\t\t\t\t\t<div id=\"copy\">\n" +
                "\t\t\t\t\t\t\t\n" +
                "\n" +
                "<!--\n" +
                "\tDesarrollador:\t\t    Ing. Ramon Alejandro Quiroz Quiroz\n" +
                "\tFecha:\t\t\t        13 Nov 2008\n" +
                "\tUltima Modificaciï¿½n:\t13 Nov 2008\n" +
                "\tDescripcion:\t\t    Pagina que visualiza las calificaciones del semestre del alumno\n" +
                "-->\n" +
                "<div id=\"breadcrumbs\" style=\" position : initial; top : initial; left : initial; font-size : 0.9em; padding : 10px 20px;\">\n" +
                "\t\t\t\t\t\t\t<span id=\"ctl00_smPath\"><a href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/Informacion_semestral/calificaciones_sem.aspx#ctl00_smPath_SkipLink\"><img alt=\"Omitir vínculos de navegación\" height=\"0\" width=\"0\" src=\"./Calificaciones del semestre SAES_files/WebResource(2).axd\" style=\"border-width:0px;\"></a><span><a title=\"Inicio\" href=\"https://www.saes.upiicsa.ipn.mx/default.aspx\">Inicio</a></span><span>&nbsp;»&nbsp;</span><span><a title=\"Alumnos\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/default.aspx\">Alumnos</a></span><span>&nbsp;»&nbsp;</span><span><a title=\"Informacion del periodo escolar en curso\" href=\"https://www.saes.upiicsa.ipn.mx/Alumnos/Informacion_semestral/default.aspx\">Inscripción Actual</a></span><span>&nbsp;»&nbsp;</span><span>Calificaciones</span><a id=\"ctl00_smPath_SkipLink\"></a></span>\n" +
                "\t\t\t\t\t\t</div><div class=\"container\" style=\"min-width: 680px;\">\n" +
                "    <br>\n" +
                "    <span id=\"ctl00_mainCopy_Lbl_General\"><center>    <table width=\"100%\">        <tbody><tr>            <td rowspan=\"2\" align=\"center\">                <img src=\"./Calificaciones del semestre SAES_files/Poli_XCH(1).png\" alt=\"IPN\">            </td>            <td align=\"center\">                <b>UNIDAD PROFESIONAL INTERDISCIPLINARIA DE INGENIERÍA Y</b>            </td>            <td rowspan=\"2\" align=\"center\">                <img src=\"./Calificaciones del semestre SAES_files/60(1).png\" alt=\"Escuela\" height=\"60\" width=\"60\">            </td>        </tr>        <tr>            <td align=\"center\">                <b>CIENCIAS SOCIALES Y ADMINISTRATIVAS</b>            </td>        </tr>    </tbody></table></center></span>\n" +
                "    <br>\n" +
                "    <br>\n" +
                "    <span id=\"ctl00_mainCopy_Lbl_Nombre\">    <table>        <tbody><tr>            <td>                <b>BOLETA: </b>            </td>            <td>                2014031391            </td>        </tr>        <tr>            <td>                <b>NOMBRE: </b>            </td>            <td>                ANGEL VALLEJO CHAPARRO            </td>        </tr>    </tbody></table></span>\n" +
                "    <br>\n" +
                "    \n" +
                "    <br>\n" +
                "    <br>\n" +
                "    <center>\n" +
                "    \n" +
                "    </center>\n" +
                "    <div>\n" +
                "\t<table cellspacing=\"0\" cellpadding=\"4\" align=\"Center\" rules=\"cols\" border=\"1\" id=\"ctl00_mainCopy_GV_Calif\" style=\"color:Black;background-color:White;border-color:#DEDFDE;border-width:1px;border-style:Solid;border-collapse:collapse;\">\n" +
                "\t\t<tbody><tr style=\"color:White;background-color:#FF9900;font-family:Arial;font-weight:bold;\">\n" +
                "\t\t\t<th scope=\"col\">Grupo</th><th scope=\"col\">Materia</th><th scope=\"col\">1er Parcial</th><th scope=\"col\">2o Parcial</th><th scope=\"col\">3er Parcial</th><th scope=\"col\">Ext</th><th scope=\"col\">Final</th>\n" +
                "\t\t</tr><tr style=\"color:#333333;background-color:White;font-family:Arial;font-size:X-Small;\">\n" +
                "\t\t\t<td>5AV81</td><td>FORMULACION Y EVALUACION DE PROYECTOS</td><td>9</td><td>9</td><td>9</td><td>NP</td><td>9</td>\n" +
                "\t\t</tr><tr style=\"color:#333333;background-color:#F2F2F2;font-family:Arial;font-size:X-Small;\">\n" +
                "\t\t\t<td>4NM62</td><td>SEGURIDAD DE REDES</td><td>10</td><td>9</td><td>8</td><td>-</td><td>9</td>\n" +
                "\t\t</tr><tr style=\"color:#333333;background-color:White;font-family:Arial;font-size:X-Small;\">\n" +
                "\t\t\t<td>4NV70</td><td>HABILIDADES DIRECTIVAS</td><td>8</td><td>10</td><td>10</td><td>NP</td><td>9</td>\n" +
                "\t\t</tr><tr style=\"color:#333333;background-color:#F2F2F2;font-family:Arial;font-size:X-Small;\">\n" +
                "\t\t\t<td>4NV70</td><td>VIROLOGIA Y CRIPTOGRAFIA</td><td>9</td><td>8</td><td>9</td><td>-</td><td>9</td>\n" +
                "\t\t</tr><tr style=\"color:#333333;background-color:White;font-family:Arial;font-size:X-Small;\">\n" +
                "\t\t\t<td>4NM80</td><td>APLICACIONES DE REDES</td><td>10</td><td>10</td><td>10</td><td>-</td><td>10</td>\n" +
                "\t\t</tr><tr style=\"color:#333333;background-color:#F2F2F2;font-family:Arial;font-size:X-Small;\">\n" +
                "\t\t\t<td>5NM80</td><td>INFORMATICA EMPRESARIAL</td><td>10</td><td>10</td><td>10</td><td>10</td><td>10</td>\n" +
                "\t\t</tr><tr style=\"color:#333333;background-color:White;font-family:Arial;font-size:X-Small;\">\n" +
                "\t\t\t<td>5NV70</td><td>INGENIERIA DE PRUEBAS</td><td>8</td><td>10</td><td>10</td><td>-</td><td>9</td>\n" +
                "\t\t</tr><tr style=\"color:#333333;background-color:#F2F2F2;font-family:Arial;font-size:X-Small;\">\n" +
                "\t\t\t<td>2NM61</td><td>FUNDAMENTOS DE I. A.</td><td>10</td><td>10</td><td>10</td><td>-</td><td>10</td>\n" +
                "\t\t</tr><tr style=\"color:#333333;background-color:White;font-family:Arial;font-size:X-Small;\">\n" +
                "\t\t\t<td>3NV71</td><td>INGENIERIA DEL CONOCIMIENTO</td><td>9</td><td>9</td><td>9</td><td>-</td><td>9</td>\n" +
                "\t\t</tr><tr style=\"color:#333333;background-color:#F2F2F2;font-family:Arial;font-size:X-Small;\">\n" +
                "\t\t\t<td>3NM61</td><td>SEGURIDAD INFORMATICA</td><td>9</td><td>10</td><td>9</td><td>-</td><td>9</td>\n" +
                "\t\t</tr><tr style=\"color:#333333;background-color:White;font-family:Arial;font-size:X-Small;\">\n" +
                "\t\t\t<td>2NM71</td><td>ELECTIVA</td><td>-</td><td>-</td><td>AC</td><td>-</td><td>AC</td>\n" +
                "\t\t</tr>\n" +
                "\t</tbody></table>\n" +
                "</div>\n" +
                "    <center>    \n" +
                "        <strong>\n" +
                "                          \n" +
                "                          \n" +
                "        </strong>\n" +
                "    </center>\n" +
                "    <center>\n" +
                "        \n" +
                "            \n" +
                "            \n" +
                "        \n" +
                "    </center>\n" +
                "</div>  \n" +
                "\n" +
                "\t\t\t\t\t\t</div>\n" +
                "\t\t\t\t\t<div id=\"footer\">\n" +
                "\t\t\t\t\t© 2008 SAES <a href=\"http://www.ipn.mx/\" title=\"Instituto Politécnico Nacional\" target=\"_blank\">IPN</a> <a href=\"http://www.dae.ipn.mx/\" title=\"Dirección de Administración Escolar\" target=\"_blank\">IPN-DAE</a></div></div>\n" +
                "\t\t\t\t\t\n" +
                "\t\t\t\t</div>\n" +
                "\t\t\t\t<div id=\"rightcolumn\" style=\"vertical-align: top; display: table-cell; float: none;\">\n" +
                "\t\t\t\t\t<div id=\"rightcolcontainer\">\n" +
                "\t\t\t\t\t\t\n" +
                "\t\t\t\t\t\t\t\n" +
                "                            \n" +
                "                            <br>\n" +
                "                            <div class=\"sidebarcontainer\">\n" +
                "                                <h4>\n" +
                "                                    Accesos rápidos</h4>\n" +
                "                               \n" +
                "\t\t                        <!-- ++++++++++++++++++++++++++++++++++++++++++ -->\n" +
                "\t\t                        <!-- ++++++++++++++++++++++++++++++++++++++++++ -->\n" +
                "\t\t                        <!-- ++++++++++++++++++++++++++++++++++++++++++ -->\n" +
                "\t\t\t\t\t\t\t\t\n" +
                "                                \n" +
                "                                <ul>\n" +
                "                                    <li><a href=\"https://www.saes.upiicsa.ipn.mx/Academica/agenda_escolar.aspx\" roles=\"ALUMNOS,Profesores,Profesores_Posgrado,ALUMNOS_BAJA,ALUMNOS_EGRESADOS,Alumnos_Posgrado,EJECUTIVOS,CEC,EVALUACION,ESTRUCTURA,CAMBIO,CATALOGOS,Padres,TUTORES,COORDINADOR_TUTORES,UPEV,ESTADISTICAS\" title=\"Agenda escolar.\">» Agenda escolar</a></li>\n" +
                "                                    <li><a href=\"https://www.saes.upiicsa.ipn.mx/Academica/horarios.aspx\" roles=\"ALUMNOS,Profesores,Profesores_Posgrado,ALUMNOS_BAJA,ALUMNOS_EGRESADOS,Alumnos_Posgrado,EJECUTIVOS,CEC,EVALUACION,ESTRUCTURA,CAMBIO,CATALOGOS,Padres,TUTORES,COORDINADOR_TUTORES,UPEV,ESTADISTICAS\" title=\"Explorar los horarios de clase.\">» Horarios de clase</a></li>\n" +
                "                                    <li><a href=\"https://www.saes.upiicsa.ipn.mx/Academica/calendario_ets.aspx\" roles=\"ALUMNOS,Profesores,Profesores_Posgrado,ALUMNOS_BAJA,ALUMNOS_EGRESADOS,Alumnos_Posgrado,EJECUTIVOS,CEC,EVALUACION,ESTRUCTURA,CAMBIO,CATALOGOS,Padres,TUTORES,COORDINADOR_TUTORES,UPEV,ESTADISTICAS\" title=\"Explorar los horarios de ETS.\">» Horarios de ETS</a></li>\n" +
                "                                </ul>\n" +
                "                           \n" +
                "\t\t                        <!-- ++++++++++++++++++++++++++++++++++++++++++ -->\n" +
                "\t\t                        <!-- ++++++++++++++++++++++++++++++++++++++++++ -->\n" +
                "\t\t                        <!-- ++++++++++++++++++++++++++++++++++++++++++ -->\n" +
                "                           \n" +
                "                            \n" +
                "                            </div>\n" +
                "\t\t\t\t\t\t\n" +
                "\t\t\t\t\t</div>\n" +
                "\t\t\t\t</div>\n" +
                "\t\t\t\t\n" +
                "\t\t\t</div>\n" +
                "\t\t</div>\n" +
                "\t\n" +
                "\n" +
                "<script type=\"text/javascript\">\n" +
                "//<![CDATA[\n" +
                "var ctl00_mainmenu_Data = new Object();\n" +
                "ctl00_mainmenu_Data.disappearAfter = 500;\n" +
                "ctl00_mainmenu_Data.horizontalOffset = 0;\n" +
                "ctl00_mainmenu_Data.verticalOffset = 0;\n" +
                "ctl00_mainmenu_Data.staticHoverClass = 'ctl00_mainmenu_9 hover';\n" +
                "ctl00_mainmenu_Data.staticHoverHyperLinkClass = 'ctl00_mainmenu_8 hover';\n" +
                "ctl00_mainmenu_Data.iframeUrl = '/WebResource.axd?d=cKU2vWVl6n3QFjshGggWyEtekvEW3RUJhKvLqStimNHzKz1zKpkkkH-yyckgtUoSfcS3pOnuC3VfzdzTZwrRhPrdipc1&t=636284453597151108';\n" +
                "var ctl00_subMenu_Data = new Object();\n" +
                "ctl00_subMenu_Data.disappearAfter = 500;\n" +
                "ctl00_subMenu_Data.horizontalOffset = 0;\n" +
                "ctl00_subMenu_Data.verticalOffset = 0;\n" +
                "ctl00_subMenu_Data.hoverClass = 'ctl00_subMenu_16 hover';\n" +
                "ctl00_subMenu_Data.hoverHyperLinkClass = 'ctl00_subMenu_15 hover';\n" +
                "ctl00_subMenu_Data.staticHoverClass = 'ctl00_subMenu_14 hover';\n" +
                "ctl00_subMenu_Data.staticHoverHyperLinkClass = 'ctl00_subMenu_13 hover';\n" +
                "ctl00_subMenu_Data.iframeUrl = '/WebResource.axd?d=cKU2vWVl6n3QFjshGggWyEtekvEW3RUJhKvLqStimNHzKz1zKpkkkH-yyckgtUoSfcS3pOnuC3VfzdzTZwrRhPrdipc1&t=636284453597151108';\n" +
                "//]]>\n" +
                "</script>\n" +
                "</form>\n" +
                "\n" +
                "\n" +
                "</body></html>", "UTF-8");
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
        }

        return gradeEntries;
    }
}
