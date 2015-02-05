package org.jboss.jbpm.performance;

import static com.jayway.restassured.RestAssured.given;

//import org.drools.core.util.codec.Base64;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.google.common.collect.Lists;
import com.jayway.restassured.response.Header;
import com.jayway.restassured.response.Response;
import com.jayway.restassured.specification.RequestSpecification;

public class LoadTestForCreatingProcesses {
  //========== CONFIGURATION ============
	final String server = "http://localhost:8080";
	final String context = "business-central";
	final String processId="simple-order-process";
	final String processCreate = "rest/runtime/dl-customer-order-service:dl-customer-order-service:1.1/process/"+processId+"/start?map_amount=1";
	final String username="mat";
	final String password="adminmonk3y!";
	final static int numberOfThreads=10;
	final static int numberOfProcessesToCreate=2000;
  //=====================================

	public static void main(String[] s) throws Exception {
		new LoadTestForCreatingProcesses().run(numberOfThreads, numberOfProcessesToCreate);
	}

	//http://localhost:8080/business-central/rest/runtime/dl-customer-order-service:dl-customer-order-service:1.0/process/order-process/start?map_amount=20
	
	public void createProcess() {
//		Generator<String> generator = net.java.quickcheck.generator.PrimitiveGenerators.letterStrings(3, 10);
		send(server + "/" + context + "/" + processCreate, Type.POST);
	}
	
	enum Type{POST,GET};
	public String send(String url, Type type){
				RequestSpecification when = given()
//				.redirects().follow(true)
//				.body(body)
//				.auth().basic("admin", "adminmonk3y!")
				.header(new Header("Authorization", "Basic "+org.apache.commons.codec.binary.Base64.encodeBase64((username+":"+password).getBytes())))
//				.header(new Header("Authorization", "Basic YWRtaW46YWRtaW5tb25rM3kh"))
				.when();
		Response response=null;
		switch(type){
			case GET:  response=when.get(url);  break;
			case POST: response=when.post(url); break;
		}
//		Response response=when.post(url);
		String responseString=response.asString();
		System.out.println("GET: " + url +" : "+response.statusLine() + " :: " + responseString);
		return responseString;
	}
	
	class MyFutureTask<T> extends FutureTask<T>{
		public MyFutureTask(Callable<T> callable) {
			super(callable);
		}
	    @Override
	    protected void done() {
	        try {
	            if (!isCancelled()) get();
	        } catch (ExecutionException e) {
	            e.printStackTrace();
	        } catch (InterruptedException e) {
	            // Shouldn't happen, we're invoked when computation is finished
	            throw new AssertionError(e);
	        }
	    }
	}
	
	public void run(int threads, int numberOfProcessesToCreate) throws Exception {
		int count=numberOfProcessesToCreate;
		List<FutureTask<Void>> tasks = new ArrayList<FutureTask<Void>>();
		ExecutorService executor = Executors.newFixedThreadPool(threads);

		for (int i = 0; i < numberOfProcessesToCreate; i++) {
			tasks.add(new MyFutureTask<Void>(new Callable<Void>() {
				public Void call() throws Exception {
					new LoadTestForCreatingProcesses().createProcess();
					return null;
				}
			}));
		}

		long start=System.currentTimeMillis();
		for (FutureTask<Void> t : tasks) {
			executor.submit(t);
		}

		for (FutureTask<Void> t : tasks) {
			while (!t.isDone()) {
				try {
					Thread.sleep(500l);
				} catch (InterruptedException sink) {
				}
			}
			count=count-1;
//			System.out.println("Task done ("+count+" request(s) remaining).");
//			System.out.print(".");
		}
		executor.shutdown();
		System.out.println("Creating "+numberOfProcessesToCreate+" processes with "+threads+" threads took "+new DecimalFormat("###,#00").format(System.currentTimeMillis()-start)+" ms");
	}
	
}
