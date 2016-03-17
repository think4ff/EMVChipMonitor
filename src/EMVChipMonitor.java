import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

public class EMVChipMonitor {

	static String propertyFile = "./bin/EMVchipMonitor.property";
	
	final static String READ = "R";
	final static String WRITE = "W";
	
	long prvLogFileLineCnt = 0; //이전 읽은 마지막 라인수.
	String emvLogFolder = null;
	String emvLogFileFullNm = null;
	String logDate = null;
	int sleepMin = 5;
	String errStr = null;
	String errTrCmd = null; //DOS bat파일 실행
	boolean isErr = false;
	String motLogFile = null;
	String emvLineCntFname = null;
	
	private EMVChipMonitor() throws Exception {
		FileInputStream ppFnm = new FileInputStream(propertyFile);
		Properties emvPp = new Properties();
		
		String fname = null;
		String motLogFname = null;
		
		try {
			emvPp.load(ppFnm);
			
			this.emvLogFolder = emvPp.getProperty("LOG_FOLDER");
			this.emvLineCntFname = emvPp.getProperty("LINE_COUNT_F");
			fname = emvPp.getProperty("LOG_FILE");
			this.sleepMin = Integer.parseInt(emvPp.getProperty("SLEEP_MIN"));
			this.errStr = emvPp.getProperty("ERR_STRING");
			this.errTrCmd = emvPp.getProperty("ERR_TR_CMD");
			motLogFname = emvPp.getProperty("MONITOR_LOG");
		} finally {
			closeQuietly(ppFnm); //close property file
		}
		
		Date now = new Date();
		SimpleDateFormat sdFormat = new SimpleDateFormat("yyyyMMdd");
		this.logDate = sdFormat.format(now);
		
		System.out.println("LOG_FILE:" + fname);
		System.out.println("SLEEP_MIN:" + sleepMin);
		System.out.println("ERR_STRING:" + errStr);

		initialValue(READ);
		
		while(true) {
			loadFullFileName(fname, motLogFname);
			readEmvLogFile(emvLogFileFullNm);
			//TODO:must fix for PROD
//			Thread.sleep(sleepMin * 1000 * 60); //for PROD(분 단위)
			Thread.sleep(sleepMin * 1000); //for TEST(초 단위)
			if(this.isErr) {
				/* EMV chip Process Kill & Restart*/
				dosCmdExecute();
				this.isErr = false;
			}
		}
		
	}
	

	private void dosCmdExecute() {
		String logStr = null;
		
		try {
			Process p = Runtime.getRuntime().exec(errTrCmd);
			
			InputStream is = p.getInputStream();
			InputStreamReader isr = new InputStreamReader(is);
			BufferedReader br = new BufferedReader(isr);
			
			String line;
			while((line=br.readLine()) != null) {
				writeLog(motLogFile, line + "\n");
			}
			int execTime = p.waitFor();
			logStr = "##__Executing time [" + execTime + "]\n";
			writeLog(motLogFile, logStr);
		} catch (Throwable tw) {
			tw.printStackTrace();
		}
	}
	
	private void readEmvLogFile(String fname) throws Exception {
		
		Date now = new Date();
		SimpleDateFormat sdFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String logDt = sdFormat.format(now);
		String logStr = null;
		
		File fn = new File(fname);
		long currLogFileLineCnt = 0;
		long checkLineCnt = 0;
		
		if(fn.exists()) {
			FileReader fr = null;
			BufferedReader br = null;
//			StringBuffer sb = new StringBuffer();
			
			try {
				fr = new FileReader(fname);
				br = new BufferedReader(fr);
				
				String temp = "";
				
				while((temp=br.readLine())!= null) {
					//sb.append(temp+"\n");
					currLogFileLineCnt++;
					
					//이전에 읽은 마지막 라인의 뒷 부분부터 체크하자.
					checkLineCnt = currLogFileLineCnt - this.prvLogFileLineCnt;
					
					if(checkLineCnt > 0 && temp.contains(this.errStr)){
						this.isErr = true;
						logStr = "[" + logDt + "] Find Error [" + temp + "]\n";
						writeLog(motLogFile, logStr);
						System.out.println(logStr);;
					}
				}
				this.prvLogFileLineCnt = currLogFileLineCnt;
				initialValue(WRITE);
				
			} catch (IOException ex) {
				throw new IOException();
			} finally {
				closeQuietly(br);
				closeQuietly(fr);
			}
		} else {
			logStr = "[" + logDt + "] There is no log file [" + fname + "]\n";
			writeLog(motLogFile, logStr);
			System.out.println(logStr);
		}
		
	}
	
	
	private void writeLog(String fname, String logStr) throws IOException {
		FileOutputStream fos = null;
		DataOutputStream dos = null;
		
		try {
			fos = new FileOutputStream(new File(fname), true);
			dos = new DataOutputStream(fos);
			
//			dos.writeChars(logStr);
			dos.writeBytes(logStr);
			dos.flush();
		} catch (FileNotFoundException fe) {
			System.out.println("There is no log file ["+ fname +"]\n");
		}
	}
	
	private void loadFullFileName(String targetLogFname, String motLogFname) {
		
		Date now = new Date();
		SimpleDateFormat sdFormat = new SimpleDateFormat("yyyyMMdd");
		String newLogDate = sdFormat.format(now);
		
		//날짜가 다르면 다른 파일을 일어야 한다.
		if(Integer.parseInt(newLogDate) > Integer.parseInt(this.logDate)) {
			this.logDate = newLogDate;
			this.prvLogFileLineCnt = 0;
			//TODO:향후 monitoring log file 삭제는 여기서..?
		}
		
		this.emvLogFileFullNm = emvLogFolder + this.logDate + "_" + targetLogFname;
		this.motLogFile = emvLogFolder + motLogFname + "." + this.logDate;
		
		System.out.println("emvLogFileFullName::" + this.emvLogFileFullNm);
		System.out.println("motLogFname::" + this.motLogFile);
	}
	
	private void initialValue(String rwType) throws Exception {
		
//		FileInputStream ppFnm = new FileInputStream(propertyFile);
//		Properties emvPp = new Properties();
//		
//		emvPp.load(ppFnm);
		
		String emvLineCntFullFname = null;
//		String emvLineCntFname = emvPp.getProperty("LINE_COUNT_F");
		String logStr = null;
		
		emvLineCntFullFname = emvLogFolder + emvLineCntFname;
		
		File lineCntfn = new File(emvLineCntFullFname);
		
		FileOutputStream fos = null;
		DataOutputStream dos = null;
		
		FileReader fr = null;
		BufferedReader br = null;
		
		if(READ.equals(rwType) && lineCntfn.exists()) {
			try {
				fr = new FileReader(emvLineCntFullFname);
				br = new BufferedReader(fr);
				
				String temp = "";
				
				if((temp=br.readLine()) != null) {
					logStr = "load the line count [" + temp + "]";
					System.out.println(logStr);
				}
				try {
					this.prvLogFileLineCnt = Long.parseLong(temp);
				} catch (Exception e) {
					System.out.println("[Error]Please check the File=>" + emvLineCntFullFname);
					throw new NumberFormatException();
				}
				
			} catch (Exception e) {
				throw new IOException();
			} finally {
				closeQuietly(br);
				closeQuietly(fr);
			}
		} else {
//			this.prvLogFileLineCnt = 0;
		}
		
		if(WRITE.equals(rwType)) {
			try {
				fos = new FileOutputStream(new File(emvLineCntFullFname), false);
				dos = new DataOutputStream(fos);
				
//				dos.writeLong(this.prvLogFileLineCnt);
				dos.writeBytes(Long.toString(this.prvLogFileLineCnt));
				dos.flush();
				
			} catch (FileNotFoundException fe) {
				System.out.println("There is no log file [" + emvLineCntFullFname + "]\n");
			}
		}
		
	}
	
	/**
	 * 
	 * @param fr
	 */
	private void closeQuietly(FileReader fr) {
		try {
			if(fr != null)
				fr.close();
		} catch(Exception e) {
		}
	}
	private void closeQuietly(BufferedReader br) {
		try {
			if(br != null)
				br.close();
		} catch(Exception e) {
		}
	}
	private void closeQuietly(FileInputStream fis) {
		try {
			if(fis != null)
				fis.close();
		} catch(Exception e) {
		}
	}
	
	
	public static void main(String[] args) {
		try {
			new EMVChipMonitor();
		} catch (Exception ex) {
			System.out.println(ex);
		}

	}

}
