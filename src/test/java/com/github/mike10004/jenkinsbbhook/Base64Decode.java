/*
 * (c) 2015 Mike Chaberski, distributed under MIT License
 */
package com.github.mike10004.jenkinsbbhook;

import com.google.common.io.BaseEncoding;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author mchaberski
 */
public class Base64Decode {
    
    public static void main(String[] args) throws Exception {
        byte[] bytes = BaseEncoding.base64().decode("YmV0dHlAZXhhbXBsZS5jb206MTIzNDU=");
        System.out.println(BaseEncoding.base16().encode(bytes));
        String str = new String(bytes);
        System.out.println(str);
    }
}
