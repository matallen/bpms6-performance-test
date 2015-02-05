package org.jboss.jbpm.performance;

import static com.jayway.restassured.RestAssured.given;
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
import org.apache.commons.codec.binary.Base64;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import com.jayway.restassured.response.Header;
import com.jayway.restassured.response.Response;
import com.jayway.restassured.specification.RequestSpecification;

//http://localhost:8080/business-central/rest/runtime/dl-customer-order-service:dl-customer-order-service:1.1/process/order-process/start?map_amount=123
//http://localhost:8080/business-central/rest/task/query?taskOwner=mat
//http://localhost:8080/business-central/rest/task/1/start
//http://localhost:8080/business-central/rest/task/1/complete?map_approved=True
/**
 * @author mallen
 */
public class LoadTestForCreatingProcessesWithHumanTasks {
  final String server = "http://localhost:9080";
	final String context = "business-central";
	final String username = "mat";
	final String password = "adminmonk3y!";
//	final String processCreate = "rest/runtime/dl-customer-order-service:dl-customer-order-service:1.1/process/simple-order-process/start?map_amount=";
//	final String processCreate = "rest/runtime/dl-customer-order-service:dl-customer-order-service:1.1/process/order-process/start?map_amount=";
	final String processCreate = "rest/runtime/com.customer.esb:new-order-service:8.0.0/process/order-process/start?map_amount=";
	final String taskQuery     = "rest/task/query?taskOwner=mat";
	final String taskStart     = "rest/task/%s/start";
	final String taskComplete  = "rest/task/%s/complete";//?map_approved=True";
	static final DecimalFormat df=new DecimalFormat("###,#00");

	// ===============================
	static int processesToCreate=500;
	static int threadsToUse=20;
	// ===============================
	// standalone.sh -Dorg.kie.executor.pool.size=5
	public static void main(String[] s) throws Exception {
		new LoadTestForCreatingProcessesWithHumanTasks().cleanupThreaded(threadsToUse);
		long start=System.currentTimeMillis();
		new LoadTestForCreatingProcessesWithHumanTasks().createProcesses(threadsToUse, processesToCreate);
		long processCreation=System.currentTimeMillis()-start;start=System.currentTimeMillis();
		new LoadTestForCreatingProcessesWithHumanTasks().startCompleteTasksThreaded(threadsToUse);
		new LoadTestForCreatingProcessesWithHumanTasks().cleanupThreaded(1);
		long startComplete=System.currentTimeMillis()-start;
		System.out.println(processesToCreate+" processes took "+df.format(processCreation) +"ms to create with "+threadsToUse+" thread(s), and start/completion took "+df.format(startComplete)+"ms");
	}
	
	/* reusing cleanup since that start/completes tasks*/
	public void startCompleteTasksThreaded(int threadsToUse) throws XPathExpressionException, SAXException, IOException, ParserConfigurationException{
	  cleanupThreaded(threadsToUse);
	}
	public void cleanupThreaded(int threadsToUse) throws XPathExpressionException, SAXException, IOException, ParserConfigurationException{
    Response r = given()
    .header(new Header("Authorization", buildCredentials(username, password)))
    .when()
    .get(server + "/" + context + "/" + taskQuery);
    String response=r.getBody().asString();
    Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(response.getBytes()));
    XPath xpath = XPathFactory.newInstance().newXPath();
    Double count=(Double)xpath.evaluate("count(/task-summary-list/task-summary)", doc, XPathConstants.NUMBER);

    List<FutureTask<Void>> tasks = new ArrayList<FutureTask<Void>>();
    ExecutorService executor = Executors.newFixedThreadPool(threadsToUse);
    for (int i=1;i<=count;i++){
      Node node=(Node)xpath.evaluate("/task-summary-list/task-summary["+i+"]",doc,XPathConstants.NODE);
      final String id               =(String)xpath.evaluate("id",node,XPathConstants.STRING);
      tasks.add(new MyFutureTask<Void>(new Callable<Void>() {
        public Void call() throws Exception {
          System.out.println(String.format("[Thread-%-4s",Thread.currentThread().getId())+"] Starting Task   [taskId="+String.format("%-5s",id)+"]");
          send(server + "/" + context + "/" + String.format(taskStart,id), Type.POST);
          Thread.sleep(1000);
          System.out.println(String.format("[Thread-%-4s",Thread.currentThread().getId())+"] Completing Task [taskId="+String.format("%-5s",id)+"]");
          send(server + "/" + context + "/" + String.format(taskComplete,id), Type.POST);
          return null;
        }
      }));
    }
    for (FutureTask<Void> t : tasks) 
        executor.submit(t);
    for (FutureTask<Void> t : tasks)
        while (!t.isDone()) {
            try {
                Thread.sleep(1000l);
            } catch (InterruptedException sink) {}
        }
    executor.shutdown();
	}
	
	private String buildCredentials(String username, String password) {
		try{
			return "Basic "+new String(Base64.encodeBase64((username + ":" + password).getBytes()), "utf-8");
		}catch(Exception e){
			throw new RuntimeException(e);
		}
	}
	
	public void createProcess() {
		String response=send(server + "/" + context + "/" + processCreate+net.java.quickcheck.generator.PrimitiveGenerators.integers(10,7000).next(), Type.POST);
		if (response.contains("SUCCESS")){
		  String id=response.substring(response.indexOf("<id>")+4, response.indexOf("</id>"));
		  System.out.println(String.format("[%-5s",Thread.currentThread().getId())+"] Created Process [processId="+String.format("%-5s",id)+"]");
		}else{
		  System.err.println("FAILED!!!: to create process. response was: "+response);
		}
	}
	
	enum Type{POST,GET};
	public String send(String url, Type type){
				RequestSpecification when = given()
				.header(new Header("Authorization", buildCredentials(username,password)))
				.when();
		Response response=null;
		switch(type){
			case GET: response=when.get(url);break;
			case POST: response=when.post(url);break;
		}
		String responseString=response.asString();
		if (response.getStatusCode()!=200)
		  System.err.println("FAILED!!!: " + url +" : "+response.statusLine() + " :: " + responseString);
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
	
	public void createProcesses(int threads, int iterations) throws Exception {
		int count=iterations;
		List<FutureTask<Void>> tasks = new ArrayList<FutureTask<Void>>();
		ExecutorService executor = Executors.newFixedThreadPool(threads);

		for (int i = 0; i < iterations; i++) {
			tasks.add(new MyFutureTask<Void>(new Callable<Void>() {
				public Void call() throws Exception {
					new LoadTestForCreatingProcessesWithHumanTasks().createProcess();
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
					Thread.sleep(1000l);
				} catch (InterruptedException sink) {
				}
			}
			count=count-1;
//			System.out.println("Task done ("+count+" request(s) remaining).");
		}
		executor.shutdown();
		System.out.println("creation duration = "+new DecimalFormat("###,#00").format(System.currentTimeMillis()-start) +"ms");
		start=System.currentTimeMillis();
		
	}
}
