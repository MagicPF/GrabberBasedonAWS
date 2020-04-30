package helloworld;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import java.net.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.*;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class PriceWatch {
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

    /**
     *
     * @param output is the String what I want to store
     * @return // the status whether success or not
     * All the steps are similar with Lab 5
     */
    public int putdata(String output){
            HashMap<String, String> items = new HashMap<>();
            String timestamp = new Date().getTime() + "";
            int status = 0;
            try {
                items.put("timestamp", timestamp);
                items.put("price",output);
                Regions region = Regions.US_EAST_2; // Ohio
                AWSCredentials credentials = new BasicAWSCredentials(
                        "AKIA2HSUWEU3YI33YMCY",
                        "uVhSqB1dYnL4loIkkvnxPFQnlKTSl1gwCffsLcab"
                );
                String bucket = "hkbu.e9205945";
                String objKey = "P_price";//object key
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
    public Object handleRequest1(final Object input, final Context context) { // Solution 1: Find lowest price from summary tag line
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "application/json");
        String firstUrlAddress = "https://www.price.com.hk/product.php?p=366266&hw=h"; //Price website
        String str = loadWebPage(firstUrlAddress); // call the loadwebpage to get the HTML content
        String []substr = str.split("price1_min\":");//directly find the minimun value of 行货
        int second_Quotation_mark = substr[1].indexOf("\"",2);//find the next "
        float price_min = Float.parseFloat(substr[1].substring(1,second_Quotation_mark)); // Price in this substring
        int startingpoint = substr[1].indexOf("\":\"",second_Quotation_mark) + 2; // find next ":" position, +2 is the starting point for max price
        second_Quotation_mark = substr[1].indexOf("\"",startingpoint+1);//end point of max price
        float price_max = Float.parseFloat(substr[1].substring(startingpoint+1,second_Quotation_mark));// I will use it in task3
        String output = Float.toString(price_min); //parse the min value for output
        int status = putdata(output);// store the data and get back the status
        return new GatewayResponse(output, headers, status); // return the output and status
    }

    public Object handleRequest2(final Object input, final Context context) { // Solution 2 Check one by one until the end or until "你可能有兴趣"
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "application/json");
        String firstUrlAddress = "https://www.price.com.hk/product.php?p=366266&hw=h"; // the Url
        String str = loadWebPage(firstUrlAddress); // get content of Website
        String []substr = str.split("data-price=\"");//Find each price
        float price_min = 999999;//initialized no camera is expensive as this value
        for(int i = 1; i < substr.length && !substr[i].contains("你可能有興趣");i++){ //After 你可能有兴趣 is about other products
            int nexttitle = substr[i].indexOf("price_label/",2); //the location of the name of image
            if(nexttitle <= 0) // protect the program from cannot find image
                continue;
            nexttitle+=12; // 12 position is for the position after: price_label/"
            if(!substr[i].substring(nexttitle,nexttitle+4).equals("hong")) // if it is not 行货
                continue;
            int second_Quotation_mark = substr[1].indexOf("\"",2);//get next "
            float price = Float.parseFloat(substr[i].substring(0,second_Quotation_mark)); // the price is in this substring
            price_min = price > price_min?price_min:price; // get the lowest value
        }
        String output = Float.toString(price_min); // parse to string for output and store
        int status = putdata(output); // store the data into S3 and get back the status
        return new GatewayResponse(output, headers, status); // return answer and status
    }

    /**
     *
     * serialize function for store data
     */
    private static String serialize(Serializable o) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(o);
        oos.close();
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }
}
