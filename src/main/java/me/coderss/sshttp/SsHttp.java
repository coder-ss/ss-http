package me.coderss.sshttp;

import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Created by ss_guo on 2017/7/21.
 *
 */
public class SsHttp {

    public class Response {

        private byte[] header;

        private byte[] body;

        public Response(byte[] header, byte[] body) {
            this.header = header;
            this.body = body;
        }

        public byte[] getHeader() {
            return header;
        }

        public byte[] getBody() {
            return body;
        }
    }

    public Response send(String ip, int port, String requestHeader, int timeOut) throws Exception {
        return send(ip, port, requestHeader, timeOut, null, null);
    }

    /**
     * 通过socket发送 http 请求
     *
     * 可能的异常：SocketTimeoutException\IllegalArgumentException\Exception\InternalError
     *
     * @param ip 目标ip或网址
     * @param port 目标端口，一般为80或443
     * @param requestHeader http请求头
     * @param timeout 超时时间(单位：秒）
     * @param proxy 代理ip，可以为空
     * @return 响应内容
     */
    public Response send(String ip, int port, String requestHeader, int timeout,
                         Proxy.Type proxyType, String proxy) throws Exception, InternalError {
        if(ip == null || ip.isEmpty() || requestHeader == null || requestHeader.isEmpty() || port < 1) {
            return null;
        }

        //选择代理
        Proxy proxyObj= createProxy(proxyType, proxy);

        //建立socket
        Socket socket = createSocket(ip, port, timeout, proxyObj);
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();

        //发送数据
        out.write(requestHeader.getBytes());
        out.flush();

        //响应头
        byte[] responseHeader = readHeader(in);
        String strHeader = new String(responseHeader);

        //body
        byte[] body;
        if(strHeader.contains("chunked")) {
            body = readChunkBody(in);
        } else if(strHeader.contains("Content-Length:")) {
            body = readBodyByLength(in, strHeader);
        } else {
            body = new byte[0];
        }

        out.close();
        in.close();
        socket.close();

        return new Response(responseHeader, body);
    }

    /**
     * Gzip 解压
     *
     * @param bytes 原始数据
     * @return 解压后的数据
     */
    public static String gzipDecode(byte[] bytes) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            GZIPInputStream gis = new GZIPInputStream(bis);

            InputStreamReader is = new InputStreamReader(gis);

            int b;
            StringBuilder ss = new StringBuilder();

            while((b = is.read()) > 0) {
                ss.append((char)b);
            }

            is.close();
            gis.close();
            bis.close();

            return ss.toString();
        } catch (IOException e) {
            return "";
        }
    }


    /**
     * 获取socket对象
     *
     * @param ip 目的ip
     * @param port 目的端口
     * @param timeout 超时时间
     * @param proxy 代理
     * @return socket对象
     * @throws IOException 抛出io异常
     */
    private Socket createSocket(String ip, int port, int timeout, Proxy proxy) throws IOException, InternalError {
        Socket socket;

        //初始化socket
        if(proxy == null) {
            socket = new Socket();
        } else {
            socket = new Socket(proxy);
        }

        //连接
        socket.connect(new InetSocketAddress(ip, port));
        //设置超时
        socket.setSoTimeout(timeout * 1000);
        //ssl连接
        if (port == 443) {
            socket = ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(socket, ip, port, false);
        }

        return socket;
    }

    /**
     * 选择代理
     *
     * @param proxyType 代理类型
     * @param strProxy 指定代理 或 为空
     * @return 返回选择的代理
     */
    private Proxy createProxy(Proxy.Type proxyType, String strProxy) throws IllegalArgumentException {
        if(proxyType == null || strProxy == null || strProxy.isEmpty()) {
            return null;
        }

        String[] strings = strProxy.split(":");

        if(strings.length != 2) {
            throw new IllegalArgumentException("proxy " + strProxy + " is not valid");
        }

        String proxyIP = strings[0];
        int proxyPort = Integer.valueOf(strings[1]);

        return new Proxy(proxyType, new InetSocketAddress(proxyIP, proxyPort));
    }

    /**
     * 读取http返回的header
     *
     * @param is 输入流
     * @return 返回头的内容
     * @throws IOException 抛异常
     */
    private byte[] readHeader(final InputStream is) throws IOException {
        //响应头
        int b;
        List<Byte> bytes = new ArrayList<>();
        while((b = is.read()) >= 0) {
            bytes.add((byte)b);

            int bssSize = bytes.size();
            if ((char)b == '\n' && bssSize > 4) {
                //header与body中间以 \r\n\r\n 分割
                if(bytes.get(bssSize - 4) == '\r' && bytes.get(bssSize - 3) == '\n'
                        && bytes.get(bssSize - 2) == '\r' && bytes.get(bssSize - 1) == '\n') {
                    break;
                }
            }
        }

        return toArray(bytes);
    }

    /**
     * 读取以chunk返回的http body
     *
     * @param is 输入流
     * @return 返回body的内容
     * @throws IOException 抛异常
     */
    private byte[] readChunkBody(final InputStream is) throws IOException {
        // 压缩块的大小，由于chunked编码块的前面是一个标识压缩块大小的16进制字符串，在开始读取前，需要获取这个大小
        int chunkSize = getChunkSize(is);

        byte[] bytes = new byte[chunkSize];

        if(chunkSize > 0) {
            bytes = readNBytes(is, chunkSize);

            //读取下一个chunk块
            return concat(bytes, readChunkBody(is));
        }

        return bytes;
    }

    /**
     * 根据Content-Length读取返回的http body
     *
     * @param is 输入流
     * @param header 返回的http header
     * @return 返回body的内容
     * @throws IOException 抛异常
     */
    private byte[] readBodyByLength(final InputStream is, final String header) throws IOException {
        int startIndex, endIndex;
        startIndex = endIndex = header.indexOf("Content-Length:") + "Content-Length:".length();
        while(header.charAt(startIndex) == ' ') {
            ++startIndex;
            ++endIndex;
        }

        while (endIndex < header.length() && Character.isDigit(header.charAt(endIndex))) {
            endIndex++;
        }
        final String CL = header.substring(startIndex, endIndex);

        int contentLength = Integer.parseInt(CL);

        return readNBytes(is, contentLength);
    }

    /**
     * 获取chunk块的大小
     *
     * @param is 输入流
     * @return chunk块的大小
     * @throws IOException 抛异常
     */
    private int getChunkSize(final InputStream is) throws IOException {
        String strLine = readLine(is).trim();
        if(strLine.equals("")) {
            strLine = readLine(is).trim();
        }

//        if(strLine.length() < 4) {
//            strLine = 0 + strLine;
//        }

        return Integer.valueOf(strLine, 16);
    }

    /**
     * 读取一行（以\r\n结束）
     *
     * @param is 输入流
     * @return 该行的内容
     * @throws IOException 抛异常
     */
    private String readLine(final InputStream is) throws IOException {
        int b;
        List<Byte> bytes = new ArrayList<>();
        while((b = is.read()) > 0) {
            bytes.add((byte)b);

            int size = bytes.size();
            if((char)b == '\n' && size > 1) {
                if(bytes.get(size - 2) == '\r' && bytes.get(size - 1) == '\n') {
                    break;
                }
            }
        }

        return new String(toArray(bytes));
    }

    /**
     * 读取指定字节数的数据
     *
     * @param is 输入流
     * @param n 指定字节数
     * @return 读取到的内容
     * @throws IOException 抛异常
     */
    private byte[] readNBytes(final InputStream is, final int n) throws IOException {
        List<Byte> streamBytes = new ArrayList<>();

        while(streamBytes.size() < n) {
            int b = is.read();
            streamBytes.add((byte)b);
        }

        return toArray(streamBytes);
    }

    private static byte[] concat(byte[]... arrays) {
        int length = 0;
        byte[][] arr$ = arrays;
        int pos = arrays.length;

        for(int i$ = 0; i$ < pos; ++i$) {
            byte[] array = arr$[i$];
            length += array.length;
        }

        byte[] result = new byte[length];
        pos = 0;
        arr$ = arrays;
        int len$ = arrays.length;

        for(int i$ = 0; i$ < len$; ++i$) {
            byte[] array = arr$[i$];
            System.arraycopy(array, 0, result, pos, array.length);
            pos += array.length;
        }

        return result;
    }

    private static byte[] toArray(List<Byte> bytes) {
        int len = bytes.size();
        byte[] array = new byte[len];

        for(int i = 0; i < len; ++i) {
            array[i] = bytes.get(i);
        }

        return array;
    }
}



