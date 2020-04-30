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

public class PriceDiff {
        public static float P_min,P_max;

        public static String ans = "";
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
     * @param diff is the current price difference
     *             This function will load the yesterday price difference and compare with the current difference
     */
    public static void CheckDiff(float diff) throws IOException, ClassNotFoundException {
        Regions region = Regions.US_EAST_2; // Setting
        AWSCredentials credentials = new BasicAWSCredentials(
                "AKIA2HSUWEU3YI33YMCY",
                "uVhSqB1dYnL4loIkkvnxPFQnlKTSl1gwCffsLcab"
        );
        String bucket = "hkbu.e9205945";
        String objKey = "diff";
        AmazonS3 s3Client = AmazonS3ClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(region)
                .build();
        if (!s3Client.doesObjectExist(bucket, objKey)) // If there is empty, which means it is the first time to run it
            ans = "Day1";
        else {
            String data = s3Client.getObjectAsString(bucket, objKey); // get the obj
            HashMap<String, String> items2 = (HashMap<String, String>) deserialize(data); // deserialize it
            float  yesterday = Float.parseFloat(items2.get(objKey)); // get the yesterday price difference
            if (diff > yesterday) ans = "Increase"; // Compare with diff
            else if (diff == yesterday) ans = "Unchanged";
            else ans = "Decrease";
        }
    }

    /**
     * This is a function get the range of 行货
     * it is the same with task1 solution1
     * @param url the URL for Price.hk
     */
    public static void getHanghuoRange(String url){
            String str = loadWebPage(url); // Get the Website content
            String []substr = str.split("price1_min\":");//directly find 行货min
            int second_Quotation_mark = substr[1].indexOf("\"",2);//next "
            P_min = Float.parseFloat(substr[1].substring(1,second_Quotation_mark)); // Price in this substring
            int startingpoint = substr[1].indexOf("\":\"",second_Quotation_mark) + 2; // find next ":" position, +2 is the starting point for max price
            second_Quotation_mark = substr[1].indexOf("\"",startingpoint+1);//end point of max price
            P_max = Float.parseFloat(substr[1].substring(startingpoint+1,second_Quotation_mark));// I will use it in task3
        }
        public Object handleRequest(final Object input, final Context context) throws IOException, ClassNotFoundException {
           Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("X-Custom-Header", "application/json");
            String firstUrlAddress = "https://www.price.com.hk/product.php?p=366266&hw=h"; // Website P
            String secondUrlAddress = "https://www.broadwaylifestyle.com/catalogsearch/result/index?q=Nikon+Z6+Body"; // Company B
            String str2 = loadWebPage(secondUrlAddress); //Get the content of Website B
            String []substr2 = str2.split("data-price-amount="); // find each price by split key words
            String output = "";
            getHanghuoRange(firstUrlAddress); // get the lowest price of 行货 on Price
            float B_min = 99999;//initiate
            for(int i = 1; i < substr2.length;i++) { // check each product from Website B
                int second_Quotation_mark = substr2[i].indexOf("\"", 2); // Find next "
                float B_price = Float.parseFloat(substr2[i].substring(1, second_Quotation_mark)); // the price is overthere
                B_min = B_min > B_price?B_price:B_min; // get the minimum price
            }
            float diff = Math.abs(P_min - B_min); // calculate difference
            CheckDiff(diff); // Check the difference with yesterday's
            int status = putdata(Float.toString(diff)); // store the current diff into S3
            return new GatewayResponse(ans, headers, status); // return the changes and status
        }

    public int putdata(String output){
        HashMap<String, String> items = new HashMap<>();
        String timestamp = new Date().getTime() + "";
        int status = 0;
        try {
            items.put("timestampNew", timestamp);
            items.put("diff",output); // difference
            Regions region = Regions.US_EAST_2;
            AWSCredentials credentials = new BasicAWSCredentials(
                    "AKIA2HSUWEU3YI33YMCY",
                    "uVhSqB1dYnL4loIkkvnxPFQnlKTSl1gwCffsLcab"
            );
            String bucket = "hkbu.e9205945";
            String objKey = "diff";//object key
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
    private static Object deserialize(String s) throws IOException, ClassNotFoundException {
        byte[] data = Base64.getDecoder().decode(s);
        ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(data));
        Object o = ois.readObject();
        ois.close();
        return o;
    }
}
