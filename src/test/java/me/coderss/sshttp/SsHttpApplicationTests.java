package me.coderss.sshttp;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.UnsupportedEncodingException;
import java.net.Proxy;

@RunWith(SpringRunner.class)
@SpringBootTest
public class SsHttpApplicationTests {

    private SsHttp ssHttp;

    private int timeout = 5;

    public SsHttpApplicationTests() {
        ssHttp = new SsHttp();
    }

    @Test
    public void contextLoads() {
    }

    @Test
    public void naive() {
        String ip = "1212.ip138.com";
        int port = 80;
        String header = "GET /ic.asp HTTP/1.1\r\n" +
                "Host: 1212.ip138.com\r\n" +
                "Connection: keep-alive\r\n" +
                "Upgrade-Insecure-Requests: 1\r\n" +
                "User-Agent: Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/55.0.2883.87 Safari/537.36\r\n" +
                "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8\r\n" +
                "Referer: http://www.ip138.com/\r\n" +
                "Accept-Encoding: gzip, deflate, sdch\r\n" +
                "Accept-Language: zh-CN,zh;q=0.8,en;q=0.6\r\n" +
                "\r\n";

        send(ip, port, header, timeout, null, null);
    }

    @Test
    public void httpsChunkGzip() {
        String ip = "www.baidu.com";
        int port = 443;
        String header = "GET / HTTP/1.1\r\n" +
                "Host: www.baidu.com\r\n" +
                "Connection: keep-alive\r\n" +
                "Upgrade-Insecure-Requests: 1\r\n" +
                "User-Agent: Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/55.0.2883.87 Safari/537.36\r\n" +
                "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8\r\n" +
                "Referer: https://www.baidu.com/\r\n" +
                "Accept-Encoding: gzip, deflate, sdch\r\n" +
                "Accept-Language: zh-CN,zh;q=0.8,en;q=0.6\r\n" +
                "\r\n";

        send(ip, port, header, timeout, null, null);
    }

    @Test
    public void httpProxy() {
        String ip = "1212.ip138.com";
        int port = 80;
        String header = "GET http://1212.ip138.com/ic.asp HTTP/1.1\r\n" +
                "Host: 1212.ip138.com\r\n" +
                "Connection: keep-alive\r\n" +
                "Upgrade-Insecure-Requests: 1\r\n" +
                "User-Agent: Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/55.0.2883.87 Safari/537.36\r\n" +
                "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8\r\n" +
                "Referer: http://www.ip138.com/\r\n" +
                "Accept-Encoding: gzip, deflate, sdch\r\n" +
                "Accept-Language: zh-CN,zh;q=0.8,en;q=0.6\r\n" +
                "\r\n";
        String proxy = "171.39.72.230:8123";

        send(ip, port, header, timeout, Proxy.Type.HTTP, proxy);
    }

    @Test
    public void httpsProxy() {
        String ip = "www.baidu.com";
        int port = 443;
        String header = "GET / HTTP/1.1\r\n" +
                "Host: www.baidu.com\r\n" +
                "Connection: keep-alive\r\n" +
                "Upgrade-Insecure-Requests: 1\r\n" +
                "User-Agent: Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/55.0.2883.87 Safari/537.36\r\n" +
                "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8\r\n" +
                "Referer: https://www.baidu.com/\r\n" +
                "Accept-Encoding: gzip, deflate, sdch\r\n" +
                "Accept-Language: zh-CN,zh;q=0.8,en;q=0.6\r\n" +
                "\r\n";
        String proxy = "171.39.72.230:8123";

        send(ip, port, header, timeout, Proxy.Type.HTTP, proxy);
    }


    private void send(String ip, int port, String header, int timeout, Proxy.Type proxyType, String proxy) {
        SsHttp.Response response = null;
        try {
            response = ssHttp.send(ip, port, header, timeout, proxyType, proxy);
        } catch (Exception | InternalError e) {
            System.out.println(e.getMessage());
            return;
        }

        System.out.println("===================== reqeust header start =====================");
        System.out.println(header);
        System.out.println("===================== reqeust header end =====================");

        System.out.println("===================== response header start =====================");
        System.out.println(new String(response.getHeader()));
        System.out.println("===================== response header end =====================");

        printBody(response.getHeader(), response.getBody());
    }

    private void printBody(byte[] header, byte[] body) {
        String strHeader = new String(header);
        String strBody = new String(body);

        System.out.println("===================== response body start =====================");
        if(strBody.contains("charset=gb2312")) {
            try {
                System.out.println(new String(body, "GBK"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        } else if(strHeader.contains("gzip")) {
            System.out.println(SsHttp.gzipDecode(body));
        } else {
            System.out.println(strBody);
        }
        System.out.println("===================== response body end =====================");

    }
}
