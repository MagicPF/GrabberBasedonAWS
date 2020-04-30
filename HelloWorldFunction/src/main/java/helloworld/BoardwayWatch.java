package helloworld;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class BoardwayWatch {

        public static String loadWebPage(String urlString) {
            byte[] buffer = new byte[80*1024];
            String content = new String();
            try {
                URL url = new URL(urlString);
                URLConnection connection = url.openConnection();
                connection.setRequestProperty("User-Agent","Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
                connection.connect();
                BufferedReader r = new BufferedReader(new
                        InputStreamReader(connection.getInputStream(), Charset.forName("UTF-8")));
                String line;
                while ((line = r.readLine()) != null) {
                    content += line;
                }
            } catch (IOException e) {
                content = e.getMessage() + ": " + urlString;
            }
            return content;
        }
    public Object handleRequest(final Object input, final Context context) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "application/json");
        String firstUrlAddress = "https://www.broadwaylifestyle.com/catalogsearch/result/index?q=Nikon+Z6+Body"; // broadway for Nikon Z6 Body
        String str = loadWebPage(firstUrlAddress); // get content of Website
        String []substr = str.split("data-price-amount="); // the price is after that key words
        String output = ""; //initiate
        for(int i = 1; i < substr.length;i++) {  // check each substring
            int second_Quotation_mark = substr[i].indexOf("\"",2); // find the next "
            String price = substr[i].substring(1,second_Quotation_mark); // the price is in this substring
            output += price + " "; // save the price
        }
        int status = putdata(output); // store the data and get back the status
        return new GatewayResponse(output, headers, status); //return the answer and status
    }
    public int putdata(String output){ //store the data into S3
        HashMap<String, String> items = new HashMap<>();
        String timestamp = new Date().getTime() + "";
        int status = 0;
        try {
            items.put("timestamp", timestamp);
            items.put("price",output);
            Regions region = Regions.US_EAST_2;
            AWSCredentials credentials = new BasicAWSCredentials(
                    "AKIA2HSUWEU3YI33YMCY",
                    "uVhSqB1dYnL4loIkkvnxPFQnlKTSl1gwCffsLcab"
            );
            String bucket = "hkbu.e9205945";
            String objKey = "B_price";//object key
            AmazonS3 s3Client = AmazonS3ClientBuilder
                    .standard()
                    .withCredentials(new AWSStaticCredentialsProvider(credentials))
                    .withRegion(region)
                    .build();
            String serialized = serialize(items);
            s3Client.putObject(bucket, objKey, serialized);
            status = 200;
        }
        catch (Exception e){
            System.out.println(e.getMessage());
            e.printStackTrace();
            status = 500;
        }
        return status;
    }
    private static String serialize(Serializable o) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(o);
        oos.close();
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }
}
