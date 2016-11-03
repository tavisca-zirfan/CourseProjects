import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
public class Server {
	public static ConcurrentHashMap<String, ArrayList<Byte>> cache;
	public static ConcurrentHashMap<String, Long> cacheTime;
	public static void main(String []args){
		// Default port is 8080 if no args passed
		int portNumber = 8080;
		if(args.length>0){
			portNumber = Integer.parseInt(args[0]);
		}
		// This is the main socket in the main thread
		try (ServerSocket server = new ServerSocket(portNumber);){
			File file =new File("log.txt");
			if(file.exists()){
				file.delete();
			}
			file.createNewFile();
			// At any time there can be max of 1000 thread
			// We dont want to take all the resource
			ExecutorService executor = Executors.newFixedThreadPool(1000);
			server.setReuseAddress(true);
			// cache is in memory, we can also write it into a disk
			// disk operation will slow down and for a small proxy server in memory is fine
			cache = new ConcurrentHashMap<>();
			// cacheTime contains the original time it took us to get the resource
			// from the actual webserver over internet
			// we will calculate our improvement of caching using this
			cacheTime = new ConcurrentHashMap<>();
			// main thread keeps listening for new clients
			while(true){
				Socket client = server.accept();
				client.setSoTimeout(10000);
				// This prints info about the client
				// as given in question (c)
				StringBuilder info = new StringBuilder();
				info.append("Client Host Address: ").append(client.getInetAddress().getHostAddress()).append(System.lineSeparator())
				.append("Client Host Name: ").append(client.getInetAddress().getHostName()).append(System.lineSeparator())
				.append("Port: ").append(client.getPort()).append(System.lineSeparator())
				.append("Protocol: TCP\n")
				.append("Timeout: ").append(client.getSoTimeout()).append("ms")
				.append(System.lineSeparator());
				System.out.println(info.toString());
				// Creates a new thread for every request-response
				Runnable request = new Request(client,cache,cacheTime,System.nanoTime());
				executor.execute(request);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}

class Request implements Runnable{
	private Socket client;
	private ConcurrentHashMap<String, ArrayList<Byte>> cache;
	private ConcurrentHashMap<String, Long> cacheTime;
	private long startTime;
	public Request(Socket client,ConcurrentHashMap<String, ArrayList<Byte>> cache,
			ConcurrentHashMap<String, Long> cacheTime,long startTime) {
		this.client = client;
		this.cache = cache;
		this.cacheTime = cacheTime;
		this.startTime = startTime;
		// TODO Auto-generated constructor stub
	}
	@Override
	public void run() {
		// TODO Auto-generated method stub
		try (BufferedReader inputClientReader = new BufferedReader(new InputStreamReader(client.getInputStream()));
				OutputStream outputClient = client.getOutputStream()){
			StringBuilder builder = new StringBuilder();
			builder.append("Client Connected").append(System.lineSeparator())
			.append("Client Host Name : ").append(client.getInetAddress().getHostName()).append(System.lineSeparator())
			.append("Client Host Address : ").append(client.getInetAddress().getHostAddress()).append(System.lineSeparator())
			.append("Port : ").append(client.getPort()).append(System.lineSeparator());
			builder.append(System.lineSeparator());
			
			
			// take all the request header components
			// we use the whole header to check in cache
			// in case of multiple client we dont want to mess up with their cookies
			// if we dont check cookie, we will forward someone else's fb profile to you
			ArrayList<String> requestComponents = new ArrayList<>();
			String input;
			int requestSize = 0;
			// read all the request byte from client socket and create our header message
			while((input = inputClientReader.readLine())!=null && input.length()>0){
				requestComponents.add(input);
				requestSize+=input.length();
			}
			// for some reason we could not read any byte
			// not happened though
			if(requestComponents.isEmpty()){
				return;
			}
			String[] getReq = requestComponents.get(0).trim().split(" ");
			String reqType = getReq[0];
			// Take out the GET to know the resource we need
			// We are handling only GET
			if(!reqType.toUpperCase().equals("GET"))return;
			builder.append("Request Size : ").append(requestSize).append(System.lineSeparator());
			builder.append("Request Header").append(System.lineSeparator());
			for (String string : requestComponents) {
				builder.append(string).append(System.lineSeparator());
			}
			String commaSeparatedParams = requestComponents.stream()
				     .map(i -> i.toString())
				     .collect(Collectors.joining(", "));
			// check in the cache if it contains the header
			// if it does, return from here only
			builder.append(System.lineSeparator());
			if(cache.containsKey(commaSeparatedParams)){
				ArrayList<Byte> cachedDataObject = cache.get(commaSeparatedParams);
				byte[] cachedData = new byte[cachedDataObject.size()];
				int i=0;
				for(Byte b:cachedDataObject){
					cachedData[i++] = b;
				}
				builder.append("Response : Cached").append(System.lineSeparator());
				builder.append("Response Size : ").append(cachedData.length).append(System.lineSeparator());
				// Elapsed time is the one we took after caching
				// Original time is without caching
				// You can see the improvement in RTT
				// Improvement is there as we dont need to go over internet
				// to fetch the same thing that we already have
				// we return from here
				builder.append("Elapsed Time : ").append(System.nanoTime()-startTime).append("ns").append(System.lineSeparator());
				builder.append("Original Elapsed Time : ").append(cacheTime.get(commaSeparatedParams)).append("ns")
				.append(System.lineSeparator()).append(System.lineSeparator())
				.append(System.lineSeparator()).append(System.lineSeparator());
				StringBuilder rtt = new StringBuilder();
				rtt.append("For: ").append(getReq[1]).append("\n");
				rtt.append("RTT with Cache: ").append(System.nanoTime()-startTime).append("ns").append(System.lineSeparator());
				rtt.append("RTT without Cache: ").append(cacheTime.get(commaSeparatedParams)).append("ns\n");
				rtt.append("Improvement: ").append((cacheTime.get(commaSeparatedParams)/(System.nanoTime()-startTime))).append(" times\n");
				// this is the improvement we get
				System.out.println(rtt.toString());
				outputClient.write(cachedData,0,cachedData.length);
				log(builder.toString());
				// this sends empty line which tells its the end
				String end = "\r\n\r\n";
				outputClient.write(end.getBytes(),0,end.getBytes().length);
				outputClient.flush();
				inputClientReader.close();
				outputClient.close();
				client.close();
				return;
			}
			// Not in cache so we need to go to server
			
			URL url = new URL(getReq[1]);
			HttpURLConnection con = (HttpURLConnection)url.openConnection();
			con.setRequestMethod(reqType.toUpperCase());
			con.setDoOutput(true);
			con.setConnectTimeout(5000);
			// Write all the request headers we received from the client 
			if(requestComponents.size()>1){
				for(int i=1;i<requestComponents.size();i++){
					if(requestComponents.get(i).trim().isEmpty())
						continue;
					if(requestComponents.get(i).trim().startsWith("Accept-Encoding")&& requestComponents.get(i).toLowerCase().contains("gzip"))
						continue;
					String[] keyVal = requestComponents.get(i).trim().split(" ");
					if(keyVal.length>1)
						con.setRequestProperty(keyVal[0].substring(0, keyVal[0].length()-1).trim(), keyVal[1].trim());
					else{
						con.setRequestProperty(keyVal[0].substring(0, keyVal[0].length()-1).trim(), "");
					}
				}
			}
			// get all the response header
			// we are sending only the content currently
			// everything else is handled by proxy
			// if required write this header on the socket
			StringBuilder responseHeader = new StringBuilder();
			String responseStatus = "";
			Map<String, List<String>> map = con.getHeaderFields();
			for (String key : map.keySet()) {
				if(key==null || key.isEmpty()){
					String sHeader = map.get(key).get(0);
					if(sHeader.contains(con.getResponseCode()+"")){
						responseStatus = sHeader;
					}
					responseHeader.insert(0, sHeader);
				}
				responseHeader.append(key).append(": ")
				.append(map.get(key).get(0)).append("\r\n");
			}
			InputStream serverReader;
			boolean error = false;
			// Check if we got the correct status
			// 200 means OK
			// Over 400 is some type of error
			// This is error handling 
			// 404 is not found
			int statusCode = con.getResponseCode();
			if (statusCode >= 200 && statusCode < 400) {
			   // Create an InputStream in order to extract the response object
			serverReader = con.getInputStream();
			}
			else {
				// get the response for error like page not found
				serverReader = con.getErrorStream();
				error = true;
			}
			
			byte[] datapacket = new byte[2048];
			ArrayList<Byte> dataToBeCache = new ArrayList<>();
			int data_read = 0;
			String response = "";
			// reading data from the server
			// writing it to the client
			// and saving it in cache for future reference
			while((data_read=serverReader.read(datapacket))!=-1){
				outputClient.write(datapacket,0,data_read);
				for(int i = 0;i<data_read;i++){
					dataToBeCache.add(datapacket[i]);
					response+=(char)datapacket[i];
				}
			}
			StringBuilder info = new StringBuilder();
			// printing server info 
			// asked in (b)
			System.out.println(info.toString());
			// if error, log it 
			// asked for error handling
			if(error){
				builder.append("Error: ").append(responseStatus).append(System.lineSeparator())
				.append(System.lineSeparator()).append(System.lineSeparator())
				.append(System.lineSeparator()).append(System.lineSeparator());
				info.append("Error: ").append(responseStatus).append(System.lineSeparator());
			}else{
				// Write down all the info in the log
				builder.append("Response : Not Cached").append(System.lineSeparator());
				String host = url.getHost();
				InetAddress address = InetAddress.getByName(host);
				builder.append("Server Host Name : ").append(address.getHostName()).append(System.lineSeparator());
				builder.append("Server Host Address : ").append(address.getHostAddress()).append(System.lineSeparator());
				builder.append("Response Size : ").append(dataToBeCache.size()).append(System.lineSeparator());
				builder.append("Elapsed Time : ").append(System.nanoTime()-startTime).append("ns")
				.append(System.lineSeparator()).append(System.lineSeparator())
				.append(System.lineSeparator()).append(System.lineSeparator());
				cache.put(commaSeparatedParams, dataToBeCache);
				cacheTime.put(commaSeparatedParams, System.nanoTime()-startTime);
				
				info.append("Server Host Name: ").append(address.getHostName()).append(System.lineSeparator());
				info.append("Server Host Address: ").append(address.getHostAddress()).append(System.lineSeparator());
				info.append("Response Size: ").append(dataToBeCache.size()).append(System.lineSeparator());
				info.append("Protocol: HTTP\n");
				info.append("Timeout: ").append(con.getConnectTimeout()).append("ms");
				
			}
			log(builder.toString());
			System.out.println(info.toString());
			
			String end = "\r\n\r\n";
			outputClient.write(end.getBytes(),0,end.getBytes().length);
			outputClient.flush();
			serverReader.close();
			con.disconnect();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		}
		finally{
			try {
				client.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
			}
		}
	}
	
	private void log(String content){
		try{
			File file =new File("log.txt");
			//true = append file
			FileWriter fileWriter = new FileWriter(file.getName(),true);
	        BufferedWriter bufferWriter = new BufferedWriter(fileWriter);
	        bufferWriter.write(content);
	        bufferWriter.flush();
	        bufferWriter.close();
		}catch(Exception ex){
			ex.printStackTrace();
		}
	}
	
}
