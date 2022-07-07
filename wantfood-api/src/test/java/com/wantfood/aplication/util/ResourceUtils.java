package com.wantfood.aplication.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.springframework.util.StreamUtils;

public class ResourceUtils {
	//Lendo o arquivo json
	public static String getContentFromResource(String resourceName) {
		
	    try {
	        InputStream stream = ResourceUtils.class.getResourceAsStream(resourceName);
	        return StreamUtils.copyToString(stream, Charset.forName("UTF-8"));
	        
	    } catch (IOException e) {
	        throw new RuntimeException(e);
	    }
	}    
}
