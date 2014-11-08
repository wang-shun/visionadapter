package ext.tmt.integration.webservice.pm;


import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.StringUtils;
import org.bson.types.ObjectId; 

import com.mongodb.WriteResult;
import com.sg.visionadapter.BasicDocument;
import com.sg.visionadapter.DocumentPersistence;
import com.sg.visionadapter.FolderPersistence;
import com.sg.visionadapter.IFileProvider;
import com.sg.visionadapter.ModelServiceFactory;
import com.sg.visionadapter.PMDocument;
import com.sg.visionadapter.PMFolder;
import com.sg.visionadapter.URLFileProvider;

import ext.tmt.folder.api.FolderService;
import ext.tmt.folder.impl.FolderServiceImpl;


import ext.tmt.utils.Debug;
import ext.tmt.utils.DocUtils;
import ext.tmt.utils.FolderUtil;
import ext.tmt.utils.GenericUtil;
import ext.tmt.utils.IBAUtils;
import ext.tmt.utils.LWCUtil;
import ext.tmt.utils.VersionControlUtil;
import wt.doc.WTDocument;
import wt.enterprise.RevisionControlled;
import wt.epm.EPMDocument;
import wt.fc.Persistable;
import wt.fc.PersistenceHelper;
import wt.fc.PersistenceServerHelper;
import wt.folder.Folder;
import wt.folder.FolderEntry;
import wt.folder.FolderHelper;
import wt.iba.value.IBAHolder;
import wt.inf.container.WTContainer;
import wt.lifecycle.LifeCycleHelper;
import wt.lifecycle.LifeCycleManaged;
import wt.method.RemoteAccess;
import wt.method.RemoteMethodServer;
import wt.part.WTPart;
import wt.pdmlink.PDMLinkProduct;
import wt.session.SessionHelper;
import wt.util.WTException;
import wt.util.WTProperties;
import wt.vc.VersionControlException;
import wt.vc.VersionControlHelper;
import wt.vc.Versioned;
import wt.vc.wip.WorkInProgressHelper;



/**
 * Webservice服务实现类
 * @author public759
 *
 */
public class PMWebserviceImpl implements Serializable,RemoteAccess{
	

	
	private static final long serialVersionUID = -9012564223029784741L;


	/*文件夹服务接口*/
	 private  static FolderService folderService=new FolderServiceImpl();
	 
	 
	 private static String DEFAULT="/Default";
	 
	 
	 private static String VMUSER="PM-RW";
	 
	 

	//key:PM传递过来的类型
//	 private static Map<String,String> objMap=new HashMap<String,String>();
//	 static{
//		 objMap.put("", DOCUMENT);
//		 objMap.put("", EPMDOCUMENT);
//		 objMap.put("", PART);
//	 }
	 private static Map<String,String> stateMap=new HashMap<String,String>();
	 static{
		 stateMap.put(ConstanUtil.INWORK,ConstanUtil.WC_INWORK);
		 stateMap.put(ConstanUtil.APPROVE,ConstanUtil.WC_APPROVE);
		 stateMap.put(ConstanUtil.RELEASED,ConstanUtil.WC_RELEASED);
		 stateMap.put(ConstanUtil.DESPOSED,ConstanUtil.WC_DESPOSED);
	 }

	 
	 
	 
	 
	 
	//获得PM数据池
	 private static  ModelServiceFactory factory =new ModelServiceFactory();
	 static{
		    try {
				WTProperties wtproperties = WTProperties.getLocalProperties();
				String codebasePath= wtproperties.getProperty("wt.codebase.location");
				codebasePath=codebasePath+File.separator+"visionconf";
				factory.start(codebasePath);
			} catch (IOException e) {
				e.printStackTrace();
			}
		    
	 }
	 
	 
	 
	 
	 
	 /**
	  * 创建文件夹结构
	  * @param result
	  * @throws Exception
	  */
	 public static int  createFolderEntryList(String[] result) throws Exception{
		 int count=0;
		 if(result!=null){
			  Debug.P("----->>>>Length:"+result.length);
			 for (int i=0;i<result.length;i++) {
				 String str=result[i];
				 createFolderEntry(str);
				 count++;
			}
		 }
		    return count;
	 }

	 

	 /**
	  * 创建文件夹
	  * @param pm_id
	 * @throws Exception 
	  */
	 private  static  void createFolderEntry(String objectId) throws Exception{
		     Debug.P("----->>>>ObjectId:"+objectId);
		     Folder folderResult=null;
		    //首先得到PM Folder对象
		     if(objectId==null) {throw new IllegalArgumentException("----Args ID is Null");}
			FolderPersistence folderPersistence = factory.get(FolderPersistence.class);
			PMFolder folder=folderPersistence.get(new ObjectId(objectId));//PM文件夹对象
			boolean isSync=folder.isSync();
			PMFolder parentFolder=folder.getParentFolder();//父文件夹
			checkNull(parentFolder);
			String containerName=folder.getContainerName();//ContainerName
			ObjectId root_id=parentFolder.getRootId();
			
			Debug.P("------>>>Folder:"+folder.getCommonName()+"  ParentFolder:"+parentFolder.getCommonName()+"  ContainerName="+containerName+"  RootId="+objectId);
			String wcId=null;
			if(root_id!=null){//不是容器
				wcId=parentFolder.getPLMId();//获得父项对象Id
			}
		    
		    try{
		    	SessionHelper.manager.setAdministrator();
			    //判断父项是否为容器
		    	 Persistable persistable=null;
		    	if(!StringUtils.isEmpty(wcId)){
		    		  persistable=GenericUtil.getPersistableByOid(wcId);//根据Windchill oid获得对象类型
		    	}
		    	
		    		if(persistable!=null&&persistable instanceof Folder){
	 	            	Folder parent_Folder=(Folder)persistable;
	 	              if(!isSync){
	 	            		folderResult=FolderUtil.createSubFolder(folder.getCommonName(), null, parent_Folder, null);
	 	                 }else{
	 	                	String  oid=folder.getPLMId();
	 	                	folderResult=(Folder) GenericUtil.getPersistableByOid(oid);
	 	                 }
	 	            }else{
	 	           	    WTContainer  wtcontainer=GenericUtil.getWTContainerByName(containerName);
	            	     if(wtcontainer instanceof PDMLinkProduct){//产品库
	            		     folderResult=folderService.createFolder(DEFAULT, folder.getCommonName(), containerName, "1");
	            	    }else{//存储库
	            		      folderResult=folderService.createFolder(DEFAULT, folder.getCommonName(), containerName, "0");
	            	       }
	 	            }
	        
	            
	           //回写Windchill Folder Oid到PM系统
	            String wc_oid=folderResult.getPersistInfo().getObjectIdentifier().getStringValue();//OID
	            Debug.P("------Windchill Folder_OID:"+wc_oid);
	            folder.setPLMId(wc_oid);
	            folder.setPLMData(getObjectInfo(folderResult));
	            
	            folder.doUpdate();//修改
	            Debug.P("----->>>更新PM:("+folder.getCommonName()+")成功!");
		    }finally{
		    	SessionHelper.manager.setAuthenticatedPrincipal(VMUSER);
		    }

	 }
	 
	 /**
	  * 修改文件夹名称
	  * @param objectId 
	  * @param newFolderName 新文件夹名称
	  * @return
	  */
	 public static int modifyFolderEntry(String objectId,String newFolderName)throws Exception{
		  int count =0;
		  Debug.P("------->>>ObjectId:"+objectId);
		  checkNull(objectId);
//		   if (!RemoteMethodServer.ServerFlag) {
//	           String method = "modifyFolderEntry";
//	           String klass = PMWebserviceImpl.class.getName();
//	           Class[] types = { String.class,String.class};
//	           Object[] vals = {objectId,newFolderName};
//	           return (Integer) RemoteMethodServer.getDefault().invoke(method, klass, null, types, vals);
//	       }else{
	  		 //查询PM文件夹对象
	  		 FolderPersistence folderPersistence = factory.get(FolderPersistence.class);
	       	 PMFolder folder=folderPersistence.get(new ObjectId(objectId));//PM文件夹对象
	       	 String oldFolderName=folder.getCommonName();
	       	 checkNull(folder);
	       	 //获得Windchill 文件夹对象
	       	 String oid=folder.getPLMId();
	       	 String containerName=folder.getContainerName();
	      	 Debug.P("------->>Modify Folder:"+folder.getCommonName()+" ;OID="+oid+"   ;ContainerName="+containerName);
	      	 try{
	      		 SessionHelper.manager.setAdministrator();
		       	 if(!StringUtils.isEmpty(oid)){
		       		 Debug.P("----->>Windchill Folder_OID:"+oid);
		       		Persistable persistable=GenericUtil.getPersistableByOid(oid);
		       		if(persistable!=null){
		       			if(persistable instanceof Folder){
		       				Folder folderObj=(Folder)persistable;
		       				String folderPath=folderObj.getFolderPath();
		       				count=folderService.editFolder(folderPath, newFolderName, containerName);
		       				if(count>0){
		       					folder.doUpdate();
		       					Debug.P("------>>PM 更新 OldFolderName("+oldFolderName+") 成NewFolderName("+newFolderName+")Success!");
		       				}
		       			}
		       		}
		       	 }
	      	 }finally{
	      		 SessionHelper.manager.setAuthenticatedPrincipal(VMUSER);
	      	 }

//	       }
     	       return count;
	 }
	 
	 /**
	  * 删除文件夹包含文件夹下的对象
	 * @throws IllegalAccessException 
	 * @throws Exception 
	  * 
	  */
	 
	 public static int deleteFolderEntry(String objectId) throws Exception{
		   int count=0;
		   Debug.P("-------->>>Delete Folder:"+objectId);
		   if (!RemoteMethodServer.ServerFlag) {
	            String method = "deleteFolderEntry";
	            String klass = PMWebserviceImpl.class.getName();
	            Class[] types = { String.class,};
	            Object[] vals = {objectId};
	            return (Integer) RemoteMethodServer.getDefault().invoke(method, klass, null, types, vals);
	        }else{
	             checkNull(objectId);
	        	 FolderPersistence folderPersistence = factory.get(FolderPersistence.class);
	        	 PMFolder folder=folderPersistence.get(new ObjectId(objectId));//PM文件夹对象
	        	 checkNull(folder);
	        	try{
	                  checkNull(objectId);
		             SessionHelper.manager.setAdministrator();
		        	 Debug.P("------Ready Delete FolderName:"+folder.getCommonName());
		        	 String containerName=folder.getContainerName();
		        	 //获得Windchill的PLMID
		        	   String oid=folder.getPLMId();
		        	 if(!StringUtils.isEmpty(oid)){
		          		Persistable persistable=GenericUtil.getPersistableByOid(oid);
		          		if(persistable!=null){
		          			if(persistable instanceof Folder){//是否为文件夹类型
		          				Folder folderObj=(Folder)persistable;
		          				String folderPath=folderObj.getFolderPath();
		          				count=folderService.deleteFolder(folderPath, containerName);
		          				if(count>0){//如果Windchill删除成功则删除PM系统数据
		          					folder.doRemove();
		          					Debug.P("----Remove PM Folder:"+folder.getCommonName()+" Success!");
		          				}
		          				
		          			}
		          		}
		          	 }
	        	}finally{
	        		SessionHelper.manager.setAuthenticatedPrincipal(VMUSER);
	        	}
	
	        }
     	            return count;
	 }
	 
	 
	

	private static void checkNull(Object object) {
		if(object==null){
			Debug.P("---->>Object:"+object);
			throw new IllegalArgumentException("object  is Null");
		}
	
	}


	
	
	/**
	 * 创建WTDocument文档
	 * @param pm_docId
	 */
    public static int  createWTDocumentEntry(String pm_docId) throws Exception{
    	   int count=0;
    	   Debug.P("------>>>Create Windchill Doc:PMID("+pm_docId+")");
//		 if (!RemoteMethodServer.ServerFlag) {
//	            String method = "createWTDocumentEntry";
//	            String klass = PMWebserviceImpl.class.getName();
//	            Class[] types = { String.class,};
//	            Object[] vals = {pm_docId};
//	            return (Integer) RemoteMethodServer.getDefault().invoke(method, klass, null, types, vals);
//	     }else{
	        	WTDocument document=WTDocument.newWTDocument();
	        	//获得PM文档对象
	        	DocumentPersistence docPersistance=factory.get(DocumentPersistence.class);
	        	PMDocument pm_document=docPersistance.get(new ObjectId(pm_docId));
	        	PMFolder pmfolder=pm_document.getFolder();//获得文档所在的PM文件夹
	        	String wc_foid=pmfolder.getPLMId();//Windchill 文件夹 Oid 
	        	boolean isContainer=pmfolder.isContainer();
	        	String containerName=pmfolder.getContainerName();
	        	Debug.P("----->>>>WC   Folder ID:"+wc_foid+"  是否为PM的容器文件夹:"+isContainer +"  ;ContaienrName:"+containerName);
	        	try{
	        		SessionHelper.manager.setAdministrator();
	        		  Persistable persistable=null;
	        		  WTContainer container=null;
	        		  if(!StringUtils.isEmpty(wc_foid)){
	        				persistable=GenericUtil.getPersistableByOid(wc_foid);
	        		  }else{//创建到容器下
	        			   container=GenericUtil.getWTContainerByName(containerName);
	        			   persistable=GenericUtil.createNewPath(container);
	        		  }
	        	
	        		if(persistable instanceof Folder){//文件夹
	        			Folder folder=(Folder)persistable;
	                   //判断文档是否已创建
	       			   boolean isEmpty=StringUtils.isEmpty(pm_document.getPLMId());
	        			if(isEmpty){//新建
	            			Map ibas=new HashMap();//软属性集合
	            			setDocIBAValuesMap(ibas, pm_document);
	        				document= DocUtils.createDocument(pm_document, null,VMUSER,ibas,folder);
	        				if(isContainer){//容器
	        					GenericUtil.moveObject2Container(document, container,folder);
	        				}else{//文件夹
	        					FolderUtil.changeFolder(document,folder);
	        				}
	            		}
	            		//回写Windchill信息到PM
	            		String wcId=document.getPersistInfo().getObjectIdentifier().getStringValue();
	            		pm_document.setPLMData(getObjectInfo(document));
	            		pm_document.setPLMId(wcId);
	            		pm_document.setMajorVid(document.getVersionIdentifier().getValue());
	            		pm_document.setSecondVid(Integer.valueOf(document.getIterationIdentifier().getValue()));
	            		WriteResult result=pm_document.doUpdate();//修改
	            		Debug.P("----->>>PM Return:("+result.getN()+")Create PMID:"+wcId+"  ;PM_Document:"+pm_document.getPLMId());
	            		count=1;
	        			}
	        		
	        	}catch(Exception e){
	        		throw new Exception("Windchill 创建("+pm_document.getCommonName()+")文档失败!");
	        	}finally{
	        		SessionHelper.manager.setAuthenticatedPrincipal(VMUSER);
	        	}
         	     return count;
    }
    

    /**
     * 更新文档信息
     * @param pm_id
     * @param newDocName
     * @return
     * @throws Exception
     */
    public static int updateWTDocumentEntry(String pm_id)throws Exception{
    	Debug.P("------>>>Update Windchill Doc:PMID("+pm_id+")");
    	int count=0;
    	checkNull(pm_id);
//		if (!RemoteMethodServer.ServerFlag) {
//	            String method = "updateWTDocumentEntry";
//	            String klass = PMWebserviceImpl.class.getName();
//	            Class[] types = { String.class,};
//	            Object[] vals = {pm_id};
//	            return (Integer) RemoteMethodServer.getDefault().invoke(method, klass, null, types, vals);
//	     }else{
	    	DocumentPersistence docPersistance=factory.get(DocumentPersistence.class);
	     	PMDocument pm_document=docPersistance.get(new ObjectId(pm_id));
	     	String pm_docName=pm_document.getCommonName();
	     	
	     	if(pm_document!=null){
	     		//判断是否已经同步到Windchill
	     		boolean isCreated=pm_document.getPLMId()==null?false:true;
	     		String doc_id=pm_document.getPLMId();
	     		Debug.P("------>>>>Windchill中是否已经创建("+pm_docName+"):"+isCreated+"  Doc_Windchill:"+doc_id);
	     		try{
	     			SessionHelper.manager.setAdministrator();
	         		if(isCreated){
	         			if(StringUtils.isEmpty(doc_id)) return 0;
	         			String doc_num=(String) pm_document.getPLMData().get(ConstanUtil.NUMBER);
	         			Persistable object= GenericUtil.getObjectByNumber(doc_num);
	         			if(object!=null&&object instanceof WTDocument){
	         				WTDocument doc=(WTDocument)object;
	         				HashMap ibas=new HashMap();
	         			    setDocIBAValuesMap(ibas, pm_document);
	         				doc=(WTDocument) GenericUtil.checkout(doc);
	         				doc=DocUtils.updateWTDocument(doc,pm_document, ibas);//更新文档
	         				if (doc != null) {
	         					if (wt.vc.wip.WorkInProgressHelper.isCheckedOut(doc, wt.session.SessionHelper.manager.getPrincipal()))
	         						doc = (WTDocument) WorkInProgressHelper.service.checkin(doc, "update document Info");
	         				   }
	         				
	         			
	             			//操作完回调doUpdate()
	         				pm_document.setPLMData(getObjectInfo(doc));
	         				pm_document.setMajorVid(doc.getVersionIdentifier().getValue());
	         				pm_document.setSecondVid(Integer.valueOf(doc.getIterationIdentifier().getValue()));
	         				pm_document.doUpdate();
	         			    Debug.P("------>>>Update PM_DocumentName："+pm_docName+" Success!");
	         			}
	         		}
	     		}finally{
	     			SessionHelper.manager.setAuthenticatedPrincipal(VMUSER);
	     		}
	        }
//    	}
    	  return count;
    }


    
	
	
    /**
     * 删除文档对象
     * @param pm_docId
     * @return
     * @throws Exception
     */
    public static int deleteWTDocumentEntry(String pm_docId)throws Exception{
    	
       Debug.P("------>>>Delete PM_DocumentID："+pm_docId);
       
//  	   if (!RemoteMethodServer.ServerFlag) {
//           String method = "deleteWTDocumentEntry";
//           String klass = PMWebserviceImpl.class.getName();
//           Class[] types = { String.class,};
//           Object[] vals = {pm_docId};
//           return (Integer) RemoteMethodServer.getDefault().invoke(method, klass, null, types, vals);
//       }else{
    	   
    	checkNull(pm_docId);
       	DocumentPersistence docPersistance=factory.get(DocumentPersistence.class);
       	PMDocument pm_document=docPersistance.get(new ObjectId(pm_docId));
       	checkNull(pm_document);
       	 //获得PM 文档对应的Windchill文档ID
       	String wc_oid=pm_document.getPLMId();
       	Debug.P("------>>>PM("+pm_docId+")<--->Windchill("+wc_oid+")");
       	try {
       		SessionHelper.manager.setAdministrator();
       		if(!StringUtils.isEmpty(wc_oid)){
       			String doc_num=(String) pm_document.getPLMData().get(ConstanUtil.NUMBER);
       			WTDocument doc=(WTDocument) GenericUtil.getObjectByNumber(doc_num);
//           	WTDocument doc=(WTDocument) GenericUtil.getPersistableByOid(wc_oid);
           		 if(doc!=null){
           			 GenericUtil.deleteDoc(doc, null);
           			 return 1;
           		 }
           	 }
   		} finally{
   			SessionHelper.manager.setAuthenticatedPrincipal(VMUSER);
   		  }
//       }
    	  return 0;
    }
    
    
    /**
     * 同步移动PM文档的路径到Windchill系统中更改
     * @param pm_docId
     * @return
     * @throws Exception
     */
    public static int moveWTDocumentEntry(String pm_docId)throws Exception{

   	 Debug.P("------>>>Move Path PM_DocumentID ："+pm_docId);
	 checkNull(pm_docId);
//	 if (!RemoteMethodServer.ServerFlag) {
//           String method = "moveWTDocumentEntry";
//           String klass = PMWebserviceImpl.class.getName();
//           Class[] types = { String.class,};
//           Object[] vals = {pm_docId};
//           return (Integer) RemoteMethodServer.getDefault().invoke(method, klass, null, types, vals);
//       }else{
    	     BasicDocument basic_object=factory.getBasicDocumentById(pm_docId);
    	 	 checkNull(basic_object);
    	 	//根据PM文件夹找到与之对应的Windchill文件夹Oid
    	      PMFolder folder=basic_object.getFolder();
    	      checkNull(folder);
    	 	 //判断文件夹是否为容器Root
    	 	 boolean isContainer=folder.isContainer();
    	 	 String containerName=folder.getContainerName();
    	 	 checkNull(folder);
    	 	 String wc_foid=folder.getPLMId();
    		 Debug.P("---->>Windchill Folder ID:"+wc_foid+"  Windchill Doc ID:"+basic_object.getPLMId());
    		 
    	 	   try {
    	 		  String doc_num=(String) basic_object.getPLMData().get(ConstanUtil.NUMBER);
    			   if(!StringUtils.isEmpty(doc_num)){
    			    	SessionHelper.manager.setAdministrator();
    			    	Persistable object= GenericUtil.getObjectByNumber(doc_num);
	         			 Folder folderObj=null;
	         			if(isContainer){//容器
	         					WTContainer container=GenericUtil.getWTContainerByName(containerName);
	         					folderObj=GenericUtil.createNewPath(container);
	         					GenericUtil.moveObject2Container(object, container, folderObj);//移动到容器目录下
	         				}else{//文件夹
	         					if(StringUtils.isEmpty(wc_foid)){throw new Exception("文档("+basic_object.getCommonName()+")目标文件夹("+folder.getCommonName()+")在Windchill系统不存在 ,无法执行移动操作!");}
	         					folderObj=(Folder) GenericUtil.getPersistableByOid(wc_foid);
	         					FolderUtil.changeFolder((FolderEntry) object, folderObj);//移动文档位置
	         				}
	    			    	PersistenceHelper.manager.refresh(folderObj);
	    			    	return 1;
    			    }
    		    } finally {
    	             SessionHelper.manager.setAuthenticatedPrincipal(VMUSER);
    		    }
//       }
	 
 	           return 0;
  }
    
    
    
	/**
	 * 映射文档对象对象软属性
	 * @param ibas
	 * @param object
	 * @throws WTException
	 */
	private  static void setDocIBAValuesMap(Map ibas,PMDocument object )throws WTException{
		//设置对象参数
		if(!StringUtils.isEmpty(object.getPhase())){
			 ibas.put(ConstanUtil.PHASE, object.getPhase());//阶段
		}
		if(!StringUtils.isEmpty(object.getProductNumber())){
			ibas.put(ConstanUtil.PROJECTNO,object.getProductNumber());//项目编号
		}
		if(!StringUtils.isEmpty(object.getProjectWorkOrder())){
			ibas.put(ConstanUtil.WORKORDER, object.getProjectWorkOrder());//工作令号
		}
		if(!StringUtils.isEmpty(object.getProductNumber())){
			  ibas.put(ConstanUtil.PRODUCTNO, object.getProductNumber());//关联的成品号
		}
	 
	}
	
	

	/**
	 * 获取对象的属性信息
	 * @param object
	 * @throws WTException
	 */
	 private static Map<String,Object> getObjectInfo(Persistable object)throws Exception{
		  Map<String,Object> result=new HashMap<String,Object>();
		  if(object!=null){
			   if(object instanceof WTPart){
				   WTPart part=(WTPart)object;
				   result.put(ConstanUtil.NAME, part.getName());
				   result.put(ConstanUtil.TYPE, part.getType());
				   result.put(ConstanUtil.NUMBER, part.getNumber());
				   result.put(ConstanUtil.CREATOR, part.getCreator().getName());
				   result.put(ConstanUtil.MODIFIER, part.getModifier().getName());
				   //获取对象的软属性集合
				   Map<String,Object> ibas=LWCUtil.getAllAttribute(part);
				   result.putAll(ibas);
			   }else if(object instanceof EPMDocument){
				   EPMDocument epm=(EPMDocument)object;
				   result.put(ConstanUtil.NAME, epm.getName());
				   result.put(ConstanUtil.TYPE, epm.getType());
				   result.put(ConstanUtil.NUMBER, epm.getNumber());
//				   result.put(ConstanUtil.STATE, epm.getLifeCycleState().getDisplay());
				   result.put(ConstanUtil.CREATOR, epm.getCreator().getName());
				   result.put(ConstanUtil.MODIFIER, epm.getModifier().getName());
				   result.put(ConstanUtil.CREATEDATE, epm.getCreateTimestamp().toString());
				   result.put(ConstanUtil.MODIFYDATE, epm.getModifyTimestamp().toString());
				   result.put(ConstanUtil.DOWNLOAD_URL, GenericUtil.getPrimaryContentUrl(epm));
				   //获取对象的软属性集合
				   Map<String,Object> ibas=LWCUtil.getAllAttribute(epm);
				   result.putAll(ibas);
			   }else if(object instanceof WTDocument){
				   WTDocument doc=(WTDocument)object;
				   Debug.P("----------->>>>Doc:"+doc.getName());
				   result.put(ConstanUtil.NAME, doc.getName());
				   result.put(ConstanUtil.TYPE, doc.getType());
				   result.put(ConstanUtil.NUMBER, doc.getNumber());
//                   result.put(ConstanUtil.STATE, doc.getLifeCycleState().getDisplay());
				   result.put(ConstanUtil.CREATOR, doc.getCreator().getName());
				   result.put(ConstanUtil.MODIFIER, doc.getModifier().getName());
//			   result.put(ConstanUtil.CREATEDATE, doc.getCreateTimestamp().toString());
//			   result.put(ConstanUtil.MODIFYDATE, doc.getModifyTimestamp().toString());
				   result.put(ConstanUtil.DOWNLOAD_URL, GenericUtil.getPrimaryContentUrl(doc));
				   //获取对象的软属性集合
				   Map<String,Object> ibas=LWCUtil.getAllAttribute(doc);
				   result.putAll(ibas);
			   }else if(object instanceof Folder){
				   Folder folder=(Folder)object;
				   result.put(ConstanUtil.NAME, folder.getName());
			   }
			     //对象链接地址
			     result.put(ConstanUtil.OBJECT_URL, GenericUtil.getObjUrl(object));
		  }
		        return result;
	 }
	
	
	 /**
	  * 获得对象的版本(A.3)
	  * @param object
	  * @return
	  */
	 public static String getObjectVersion(RevisionControlled object){
		  checkNull(object);
		  String result=null;
		 try {
			 String version =VersionControlHelper.getVersionIdentifier(object).getValue();
			 String iteration=VersionControlHelper.getIterationIdentifier(object).getValue();
	         result=version+"."+iteration;
		 } catch (VersionControlException e) {
			 Debug.P(e.getMessage());
			e.printStackTrace();
		}
		  return result;
	 }
	 
	 
	 
	 /**
	  * 修订版本
	  * @param pm_oid
	  * @throws Exception
	  */
	 public static void changeRevision(String pm_id)throws Exception{
		 
		   Debug.P("----->>Check Revision PM_DocID:"+pm_id);
		   checkNull(pm_id);
		    //获取PM文档对象
//			DocumentPersistence docPersistance=factory.get(DocumentPersistence.class);
//        	PMDocument pm_document=docPersistance.get(new ObjectId(pm_id));
             BasicDocument basic_object=factory.getBasicDocumentById(pm_id);
             checkNull(basic_object);
		   //获得WindChill ID
        	String wc_id=basic_object.getPLMId();
        	Debug.P("----------->>>Windchill PLMID:"+wc_id);
        	try {
				SessionHelper.manager.setAdministrator();
				if(!StringUtils.isEmpty(wc_id)){
				     String doc_num=(String) basic_object.getPLMData().get(ConstanUtil.NUMBER);
				     Persistable object=GenericUtil.getObjectByNumber(doc_num);
					 Folder folder = FolderHelper.service.getFolder((FolderEntry) object);
					 WTDocument newdoc= (WTDocument) VersionControlHelper.service.newVersion((Versioned) object);
//					 LifeCycleHelper.setLifeCycle(newdoc, LifeCycleHelper.service.getLifeCycleTemplate(tempName, doc.getContainerReference()));
					 FolderHelper.assignLocation(newdoc,folder);
					 PersistenceHelper.manager.save(newdoc);
					 GenericUtil.changeState(newdoc, ConstanUtil.WC_INWORK);//修订时将对象生命周期状态改为工作中
					 PersistenceHelper.manager.refresh(newdoc);
					 Debug.P("------New Revision:"+newdoc.getVersionIdentifier().getValue()+"   Iteration:"+newdoc.getIterationIdentifier().getValue());
					 basic_object.setMajorVid(newdoc.getVersionIdentifier().getValue());
					 basic_object.setSecondVid(Integer.valueOf(newdoc.getIterationIdentifier().getValue()));
					 basic_object.doUpdate();
				}
			} catch (Exception e) {
				e.printStackTrace();
				basic_object.doSetErrorMessage(10, "PLM 修订("+wc_id+")升级版本异常!");
			    throw new Exception(e.getMessage());
			}finally{
				SessionHelper.manager.setAuthenticatedPrincipal(VMUSER);
			}
        	
	 }

	 /**
	  * 修改生命周期状态
	  * @param pm_id
	  * @return
	  */
	 public static void changeLifecycleState(String pm_id)throws Exception{
		  Debug.P("----------->>>>Lifecycle  PMID:"+pm_id);
		 //获取PM文档对象
		  if(!StringUtils.isEmpty(pm_id)){
//			     DocumentPersistence docPersistance=factory.get(DocumentPersistence.class);
//		      	 PMDocument pm_document=docPersistance.get(new ObjectId(pm_id));
			     BasicDocument  basic_object=factory.getBasicDocumentById(pm_id);
		      	 checkNull(basic_object);
		      	//获得WindChill ID
		        String wc_id=basic_object.getPLMId();
		        Debug.P("----------->>>Windchill PLMID:"+wc_id);
		        try {
					  SessionHelper.manager.setAdministrator();
					  String doc_num=(String) basic_object.getPLMData().get(ConstanUtil.NUMBER);
				      Persistable object=GenericUtil.getObjectByNumber(doc_num);
				      String stateName=basic_object.getStatus();
				      object=GenericUtil.changeState((LifeCycleManaged) object, stateMap.get(stateName));
				      PersistenceHelper.manager.refresh(object);
				      basic_object.setPLMData(getObjectInfo(object));
				      basic_object.doUpdate();
		        } catch (Exception e) {
				    e.printStackTrace();
				}finally{
					SessionHelper.manager.setAuthenticatedPrincipal(VMUSER);
				}
		      	 
		  }
	 }
	 
	 
	 /**
	  * 更改对象阶段
	  * @param pm_id
	  * @throws Exception
	  */
	 public static int  changePhase(String pm_id)throws Exception{
		  Debug.P("------->>>Change Phase PMID:"+pm_id);
		   int count=0;
		  if(!StringUtils.isEmpty(pm_id)){
			  //根据pm_id修改阶段信息
			  BasicDocument object=factory.getBasicDocumentById(pm_id);
              String phase=object.getPhase();//阶段
              String plmId=object.getPLMId();//PM对应的Windchill字段
              Debug.P("----Phase--->>>Windchill ID:"+plmId+"   ;Phase Value:"+phase);
              if(!StringUtils.isEmpty(plmId)){
             try {
			    SessionHelper.manager.setAdministrator();
			    if(!StringUtils.isEmpty(phase)){
			        Persistable persistable=GenericUtil.getPersistableByOid(plmId);
//				    Map  ibas=new HashMap();
//			        ibas.put(ConstanUtil.PHASE, phase);
			        IBAUtils iba_values=new IBAUtils((IBAHolder)persistable);
			        iba_values.setIBAValue(ConstanUtil.PHASE, phase);
			        iba_values.updateIBAPart((IBAHolder)persistable);
//				    LWCUtil.setValueBeforeStore(persistable, ibas);//修改阶段属性
//				    PersistenceHelper.manager.save(persistable);
				    count=1;
			    }
			
			 } catch (Exception e) {
				 throw new Exception("Windchill ("+plmId+") 修改阶段失败!");
			}finally{
				 SessionHelper.manager.setAuthenticatedPrincipal(VMUSER);
			}       	        
        }    
	}
		   return count;
	 }
	 
	 



}