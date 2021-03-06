/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.knaw.meertens.deployment.lib;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import static javax.ws.rs.client.Entity.json;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import nl.mpi.tla.util.Saxon;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.io.FileUtils;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.json.simple.JSONArray;
import org.json.simple.parser.ParseException;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 *
 * @author vic
 */
public class Clam implements RecipePlugin {
    protected int counter = 0;
    protected Boolean isFinished = false;
    
    @Override
    public Boolean finished() {
        return isFinished;
    }
    
    @Override
    public String execute(String projectName) {
        
        JSONObject json = new JSONObject();
        json.put("key", projectName);
        json.put("status", 202);
        JSONObject userConfig = new JSONObject(); 
        try {
            userConfig = this.parseUserConfig(projectName);
            this.createProject(projectName);
            
            this.prepareProject(projectName);
            this.runProject(projectName);
            this.downloadProject(projectName);
//            this.deleteProject(key);
            
            isFinished = true;
        } catch (IOException ex ) {
            Logger.getLogger(Clam.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ParseException ex) {
            Logger.getLogger(Clam.class.getName()).log(Level.SEVERE, null, ex);
        } catch (JDOMException ex) {
            Logger.getLogger(Clam.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SaxonApiException ex) {
            Logger.getLogger(Clam.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return json.toString();
//        return userConfig.toString();
    }
    
    public JSONObject parseUserConfig(String key) throws FileNotFoundException, IOException, ParseException {
        JSONParser parser = new JSONParser();
        JSONObject userConfig = (JSONObject) parser.parse(new FileReader("/tmp/wd/"+key+"/config.json")); 
        
        
        return userConfig;
    }
    
    public void runProject(String key) throws MalformedURLException, IOException, JDOMException, ParseException {
        JSONObject json = new JSONObject();
        JSONParser parser = new JSONParser();
        String user, accessToken;
        
        json = this.getAccessToken(key);
        user = (String)json.get("user");
        accessToken = (String)json.get("accessToken");
        
        /*
        set output template
        */
        Map<String,Object> params = new LinkedHashMap<>();
        params.put("xml", "1");

        StringBuilder postData = new StringBuilder();
        for (Map.Entry<String,Object> param : params.entrySet()) {
            if (postData.length() != 0) postData.append('&');
            postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
            postData.append('=');
            postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
        }
        byte[] postDataBytes = postData.toString().getBytes("UTF-8");
        /*
        end of set output template
        */
        URL url = new URL("http://docker.for.mac.host.internal:8080/ucto/"+key+"/?user="+user+"&accesstoken="+accessToken);
        HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
        
        httpCon.setDoOutput(true);
        httpCon.setRequestMethod("POST");
        httpCon.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        httpCon.setRequestProperty("Accept", "application/json");
        httpCon.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
        httpCon.getOutputStream().write(postDataBytes);
        
//        json = (JSONObject) parser.parse(this.getUrlBody(httpCon));
//        json.put("status", httpCon.getResponseCode());
//        json.put("message", httpCon.getResponseMessage());
        String result = this.getUrlBody(httpCon);
                
        httpCon.disconnect();
    }
    
    public JSONObject prepareProject(String key) throws IOException, MalformedURLException, JDOMException, FileNotFoundException, ParseException {
        JSONObject jsonResult = new JSONObject();
        JSONObject json = new JSONObject();
        json = parseUserConfig(key);
        
        JSONArray params = (JSONArray)json.get("params");

        for (Object param : params) {
            JSONObject objParam = new JSONObject();
            objParam = (JSONObject)param;
            String inputTemplate = (String)objParam.get("name");
            String type = (String)objParam.get("type");
            String value = (String)objParam.get("value");
            
            
            JSONArray innerParams = new JSONArray();
            innerParams = (JSONArray)objParam.get("params");
            
            String author = "";
            String language = "";
            if (innerParams.get(0) instanceof JSONObject) {
                JSONObject innerParam = (JSONObject)innerParams.get(0);
                author = (String)innerParam.get("author");
                language = (String)innerParam.get("language");
            } 
            
            if ("file".equals(type)) {
                jsonResult = this.uploadFile(key, value, language, inputTemplate, author);
                jsonResult.put("key", key);
                jsonResult.put("value", value);
                jsonResult.put("language", language);
                jsonResult.put("inputTemplate", inputTemplate);
                jsonResult.put("author", author);
            }
        }
        return jsonResult;
    }

    /**
     *
     * @param key
     * @return
     * @throws IOException
     * @throws MalformedURLException
     * @throws JDOMException
     */
    @Override
    public JSONObject getStatus(String key) throws IOException, MalformedURLException, JDOMException {
        try {
            return this.pollProject(key);
        } catch (ParseException ex) {
            Logger.getLogger(Clam.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }   
    
//    public JSONObject loadConfigureFile(String wd) throws ConfigurationException {
//        JSONObject json = new JSONObject();
//        DeploymentLib dplib = new DeploymentLib();
//        String path = dplib.getWd();
//        String fullPath = path + wd;
//        
//        String defaultConfiPath = "";
//        return json;
//    }
    
    public JSONObject pollProject(String projectName) throws IOException, IOException, MalformedURLException, MalformedURLException, JDOMException, JDOMException, JDOMException, ParseException {
        JSONObject json = new JSONObject();
        JSONParser parser = new JSONParser();
        String user, accessToken;
        
        json = this.getAccessToken(projectName);
        user = (String)json.get("user");
        accessToken = (String)json.get("accessToken");
        
        URL url = new URL("http://docker.for.mac.host.internal:8080/ucto/"+projectName+"/status/?user="+user+"&accesstoken="+accessToken);
        HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
        httpCon.setDoOutput(true);
        httpCon.setRequestMethod("GET");
        
        json = (JSONObject) parser.parse(this.getUrlBody(httpCon));
        json.put("status", httpCon.getResponseCode());
        json.put("message", httpCon.getResponseMessage());
        json.put("finished", this.finished());
        
        Long completionCode = (Long)json.get("completion");
        int statusCode = (int)json.get("status");
        Boolean successCode = (Boolean)json.get("success");
        
        if (completionCode == 100 && statusCode == 200 && successCode) {
            isFinished = true;
        }
                
        httpCon.disconnect();
        return json;
    }
    
    public JSONObject getAccessToken(String projectName) throws MalformedURLException, IOException, JDOMException {
        JSONObject json = new JSONObject();
        String urlString = "http://docker.for.mac.host.internal:8080/ucto/"+projectName+"/";
        String xmlString = readStringFromURL(urlString);
        
        SAXBuilder saxBuilder = new SAXBuilder();
        Document doc = saxBuilder.build(new StringReader(xmlString));
        Element rootNode = doc.getRootElement();
        String user = rootNode.getAttributeValue("user");
        String accessToken = rootNode.getAttributeValue("accesstoken");
        
        json.put("user", user);
        json.put("accessToken", accessToken);
        
        return json;
    }
        
    public static String readStringFromURL(String requestURL) throws IOException {
        try (Scanner scanner = new Scanner(new URL(requestURL).openStream(),
                StandardCharsets.UTF_8.toString())) {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }
        
    public JSONObject uploadFile(String projectName, String filename, String language, String inputTemplate, String author) throws MalformedURLException, IOException, JDOMException, FileNotFoundException, ParseException, ParseException {
        JSONObject jsonResult = new JSONObject();
        JSONObject json = new JSONObject();
        json = this.getAccessToken(projectName);
        
        String path = Paths.get("/tmp/wd/", projectName, "/input/", filename).normalize().toString();
        System.out.println(path);
        
        jsonResult.put("pathUploadFile", path);
        File file = new File(path);
        String filenameOnly = file.getName();
        jsonResult.put("filenameOnly", filenameOnly);
        
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        String accessToken = (String)json.get("accessToken");
        String payload = new String(encoded, "UTF-8");
        URL url = new URL("http://docker.for.mac.host.internal:8080/ucto/"+projectName+"/upload/?inputtemplate="+inputTemplate+"&user=anonymous&accesstoken="+accessToken+"&language="+language+"&documentid=&author="+author+"&filename="+filenameOnly);

        try {
            
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), "UTF-8");
            writer.write(payload);
            writer.close();
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuffer jsonString = new StringBuffer();
            String line;
            while ((line = br.readLine()) != null) {
                jsonString.append(line);
            }
            br.close();
            connection.disconnect();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }

//        HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
//        httpCon.setDoOutput(true);
//        httpCon.setRequestMethod("POST");
//        OutputStreamWriter out = new OutputStreamWriter(httpCon.getOutputStream());
//        out.write("Resource content");
//        out.close();
//        httpCon.getInputStream();
//        json.put("status", httpCon.getResponseCode());
//        json.put("message", httpCon.getResponseMessage());
//        httpCon.disconnect();
        return jsonResult;
    }
    
    public JSONObject getOutputFiles(String projectName) throws MalformedURLException, IOException, JDOMException, SaxonApiException {
        JSONObject json = new JSONObject();
        String urlString = "http://docker.for.mac.host.internal:8080/ucto/"+projectName+"/";

        Map<String,String> NS = new LinkedHashMap<>();
        NS.put("xlink", "http://www.w3.org/1999/xlink");
        XdmNode doc = Saxon.buildDocument(new StreamSource(urlString));
        for (XdmItem file:Saxon.xpath(doc, "/clam/output/file")) {
            String href = Saxon.xpath2string(file, "@xlink:href", null, NS);
            String name = Saxon.xpath2string(file, "name");
            json.put(name, href);
        }
        
        return json;
    
    }
        
    public void downloadProject(String projectName) throws MalformedURLException, IOException, JDOMException, SaxonApiException {
        JSONObject jsonFiles = this.getOutputFiles(projectName);
        String path = Paths.get("/tmp/wd/" + projectName + "/output/").normalize().toString();
        
        /* create output directory if not there */
        File theDir = new File(path);
        // if the directory does not exist, create it
        if (!theDir.exists()) {
            try{
                theDir.mkdir();
            } 
            catch(SecurityException se){
                System.err.println(se.getMessage());
            }        
        }
        /* end create output directory */
        
        Set<String> keys = jsonFiles.keySet();

        keys.forEach((key) -> {
            File file = new File(Paths.get(path, key).normalize().toString());
            System.out.println(Paths.get(path, key).normalize().toString());
            URL url;
            try {
                String urlString = (String)jsonFiles.get(key);
                urlString = urlString.replace("127.0.0.1", "docker.for.mac.host.internal");
                url = new URL(urlString);
                FileUtils.copyURLToFile(url, file, 10000, 10000);
            } catch (MalformedURLException ex) {
                Logger.getLogger(Clam.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(Clam.class.getName()).log(Level.SEVERE, null, ex);
            } 
            
        });
        
    }
    
    public JSONObject createProject(String projectName) throws MalformedURLException, IOException {
        JSONObject json = new JSONObject();
        URL url = new URL("http://docker.for.mac.host.internal:8080/ucto/"+projectName+"/");
        HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
        httpCon.setDoOutput(true);
        httpCon.setRequestMethod("PUT");
        OutputStreamWriter out = new OutputStreamWriter(httpCon.getOutputStream());
        out.write("Resource content");
        out.close();
        httpCon.getInputStream();
        json.put("status", httpCon.getResponseCode());
        json.put("message", httpCon.getResponseMessage());
        httpCon.disconnect();
        return json;
    }
    
    public JSONObject deleteProject(String projectName) throws MalformedURLException, IOException {
        JSONObject json = new JSONObject();
        URL url = new URL("http://docker.for.mac.host.internal:8080/ucto/"+projectName+"/");
        HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
        httpCon.setDoOutput(true);
        httpCon.setRequestProperty("Content-Type", "application/x-www-form-urlencoded" );
        httpCon.setRequestMethod("DELETE");
        json.put("status", httpCon.getResponseCode());
        json.put("message", httpCon.getResponseMessage());
        httpCon.disconnect();
        return json;
    }
    
    public String getUrlBody(HttpURLConnection conn) throws IOException {

        // handle error response code it occurs
        int responseCode = conn.getResponseCode();
        InputStream inputStream;
        if (200 <= responseCode && responseCode <= 299) {
            inputStream = conn.getInputStream();
        } else {
            inputStream = conn.getErrorStream();
        }

        BufferedReader in = new BufferedReader(
            new InputStreamReader(
                inputStream));

        StringBuilder response = new StringBuilder();
        String currentLine;

        while ((currentLine = in.readLine()) != null) 
            response.append(currentLine);

        in.close();

        return response.toString();
    }
    
    public JSONObject parseSymantics(String symantics) throws JDOMException, IOException, SaxonApiException {
        JSONObject json = new JSONObject();
        JSONObject parametersJson = new JSONObject();

        Map<String,String> NS = new LinkedHashMap<>();
        NS.put("cmd", "http://www.clarin.eu/cmd/1");
        NS.put("cmdp", "http://www.clarin.eu/cmd/1/profiles/clarin.eu:cr1:p_1505397653795");
        
        StringReader reader = new StringReader(symantics);
        XdmNode service = Saxon.buildDocument(new StreamSource(reader));

        String serviceName = Saxon.xpath2string(service, "cmdp:Name",null,NS);
        String serviceDescription = Saxon.xpath2string(service, "//cmdp:Service/cmdp:Description",null,NS);
        String serviceLocation = Saxon.xpath2string(service, "//cmdp:ServiceDescriptionLocation/cmdp:Location",null,NS);
                
        int counter = 0;
        for (XdmItem param:Saxon.xpath(service,"//cmdp:Operation[cmdp:Name='main']/cmdp:Input/cmdp:Parameter",null,NS)) {
            
            JSONObject parameterJson = new JSONObject();
            String parameterName = Saxon.xpath2string(param, "cmdp:Name",null,NS);
            String parameterDescription = Saxon.xpath2string(param, "cmdp:Description",null,NS);
            String parameterType = Saxon.xpath2string(param, "cmdp:MIMEType",null,NS);
            
            parameterJson.put("parameterName", parameterName);
            parameterJson.put("parameterDescription", parameterDescription);
            parameterJson.put("parameterType", parameterType);
            
            int valueCounter = 0;
            for (XdmItem paramValue:Saxon.xpath(service,"//cmdp:Operation[cmdp:Name='main']/cmdp:Input/cmdp:Parameter/cmdp:ParameterValue",null,NS)) {
                
                JSONObject parameterValueJson = new JSONObject();
                
                String parameterValueValue = Saxon.xpath2string(paramValue, "cmdp:Value",null,NS);
                String parameterValueDescription = Saxon.xpath2string(paramValue, "cmdp:Description",null,NS);
                
                parameterValueJson.put("parameterValueValue", parameterValueValue);
                parameterValueJson.put("parameterValueDescription", parameterValueDescription);
                
                parameterJson.put("value"+Integer.toString(valueCounter), parameterValueJson);
                
                valueCounter++;
            }
            
            parametersJson.put("parameter"+Integer.toString(counter), parameterJson);
            
            counter++;
        }

        json.put("serviceName", serviceName);
        json.put("serviceDescription", serviceDescription);
        json.put("serviceLocation", serviceLocation);
        json.put("counter", counter);
        json.put("parameters", parametersJson);
        return json;
        
    }
    
    public JSONObject getSymanticsFromDb() {
        JSONObject json = new JSONObject();

        return json;
        
    }
    
    public Boolean compareSymantics(JSONObject dbSymantics, JSONObject userSymantics) {
        
        return false;
    }
    
}
