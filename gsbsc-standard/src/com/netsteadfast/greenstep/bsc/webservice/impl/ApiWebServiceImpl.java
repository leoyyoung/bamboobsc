/* 
 * Copyright 2012-2016 bambooCORE, greenstep of copyright Chen Xin Nien
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * -----------------------------------------------------------------------
 * 
 * author: 	Chen Xin Nien
 * contact: chen.xin.nien@gmail.com
 * 
 */
package com.netsteadfast.greenstep.bsc.webservice.impl;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;

import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.jaxrs.ext.MessageContext;
import org.apache.shiro.subject.Subject;
import org.springframework.stereotype.Service;

import com.netsteadfast.greenstep.base.AppContext;
import com.netsteadfast.greenstep.base.Constants;
import com.netsteadfast.greenstep.base.SysMessageUtil;
import com.netsteadfast.greenstep.base.exception.ServiceException;
import com.netsteadfast.greenstep.base.model.ChainResultObj;
import com.netsteadfast.greenstep.base.model.DefaultResult;
import com.netsteadfast.greenstep.base.model.GreenStepSysMsgConstants;
import com.netsteadfast.greenstep.base.model.YesNo;
import com.netsteadfast.greenstep.base.service.logic.BscBaseLogicServiceCommonSupport;
import com.netsteadfast.greenstep.bsc.command.KpiReportBodyCommand;
import com.netsteadfast.greenstep.bsc.model.BscStructTreeObj;
import com.netsteadfast.greenstep.bsc.service.IEmployeeService;
import com.netsteadfast.greenstep.bsc.service.IOrganizationService;
import com.netsteadfast.greenstep.bsc.service.IVisionService;
import com.netsteadfast.greenstep.bsc.util.PerformanceScoreChainUtils;
import com.netsteadfast.greenstep.bsc.vo.BscApiServiceResponse;
import com.netsteadfast.greenstep.bsc.webservice.ApiWebService;
import com.netsteadfast.greenstep.model.UploadTypes;
import com.netsteadfast.greenstep.po.hbm.BbEmployee;
import com.netsteadfast.greenstep.po.hbm.BbOrganization;
import com.netsteadfast.greenstep.po.hbm.BbVision;
import com.netsteadfast.greenstep.sys.WsAuthenticateUtils;
import com.netsteadfast.greenstep.util.ApplicationSiteUtils;
import com.netsteadfast.greenstep.util.UploadSupportUtils;
import com.netsteadfast.greenstep.vo.EmployeeVO;
import com.netsteadfast.greenstep.vo.OrganizationVO;
import com.netsteadfast.greenstep.vo.VisionVO;

@Service("bsc.webservice.ApiWebService")
@WebService
@SOAPBinding
@Path("/")
@Produces("application/json")
public class ApiWebServiceImpl implements ApiWebService {
	
    @Context
    private MessageContext messageContext;	
	
	private void processForScorecard(
			BscApiServiceResponse responseObj, 
			HttpServletRequest request,
			String visionOid, String startDate, String endDate, String startYearDate, String endYearDate, String frequency, 
			String dataFor, String measureDataOrganizationOid, String measureDataEmployeeOid) throws ServiceException, Exception {
		
		org.apache.commons.chain.Context context = PerformanceScoreChainUtils.getContext(
				visionOid, startDate, endDate, startYearDate, endYearDate, frequency, dataFor, measureDataOrganizationOid, measureDataEmployeeOid);			
		ChainResultObj result = PerformanceScoreChainUtils.getResult(context);			
		if (result.getValue() == null || ( (BscStructTreeObj)result.getValue() ).getVisions() == null || ( (BscStructTreeObj)result.getValue() ).getVisions().size() == 0) {
			return;
		}		
		BscStructTreeObj resultObj = (BscStructTreeObj)result.getValue();
		KpiReportBodyCommand reportBody = new KpiReportBodyCommand();
		reportBody.execute(context);
		Object htmlBody = reportBody.getResult(context);
		if (htmlBody != null && htmlBody instanceof String) {
			String htmlUploadOid = UploadSupportUtils.create(
					Constants.getSystem(), UploadTypes.IS_TEMP, false, String.valueOf(htmlBody).getBytes(), "KPI-HTML-REPORT.html");
			String url = ApplicationSiteUtils.getBasePath(Constants.getSystem(), request);
			if (!url.endsWith("/")) {
				url += "/";
			}			
			url += "bsc.printContentAction.action?oid=" + htmlUploadOid;
			responseObj.setHtmlBodyUrl(url);
		}
		VisionVO visionObj = resultObj.getVisions().get(0);
		responseObj.setSuccess(YesNo.YES);
		responseObj.setVision(visionObj);		
	}

	@WebMethod
	@GET
	@Path("/scorecard1/")	
	@Override
	public BscApiServiceResponse getScorecard1(
			@WebParam(name="visionOid") @PathParam("visionOid") String visionOid, 
			@WebParam(name="startDate") @PathParam("startDate") String startDate, 
			@WebParam(name="endDate") @PathParam("endDate") String endDate, 
			@WebParam(name="startYearDate") @PathParam("startYearDate") String startYearDate, 
			@WebParam(name="endYearDate") @PathParam("endYearDate") String endYearDate, 
			@WebParam(name="frequency") @PathParam("frequency") String frequency, 
			@WebParam(name="dataFor") @PathParam("dataFor") String dataFor, 
			@WebParam(name="measureDataOrganizationOid") @PathParam("measureDataOrganizationOid") String measureDataOrganizationOid, 
			@WebParam(name="measureDataEmployeeOid") @PathParam("measureDataEmployeeOid") String measureDataEmployeeOid) throws Exception {
		
		HttpServletRequest request = messageContext.getHttpServletRequest();
		Subject subject = null;
		BscApiServiceResponse responseObj = new BscApiServiceResponse();
		responseObj.setSuccess( YesNo.NO );
		try {	
			subject = WsAuthenticateUtils.login();			
			this.processForScorecard(
					responseObj, 
					request,
					visionOid, startDate, endDate, startYearDate, endYearDate, frequency, dataFor, measureDataOrganizationOid, measureDataEmployeeOid);
		} catch (Exception e) {
			responseObj.setMessage( e.getMessage() );
		} finally {
			if (!YesNo.YES.equals(responseObj.getSuccess())) {
				responseObj.setMessage( SysMessageUtil.get(GreenStepSysMsgConstants.SEARCH_NO_DATA) );
			}	
			WsAuthenticateUtils.logout(subject);			
		}
		subject = null;
		return responseObj;
	}
	
	@WebMethod
	@GET
	@Path("/scorecard2/")	
	@Override
	public BscApiServiceResponse getScorecard2(
			@WebParam(name="visionId") @PathParam("visionId") String visionId, 
			@WebParam(name="startDate") @PathParam("startDate") String startDate, 
			@WebParam(name="endDate") @PathParam("endDate") String endDate, 
			@WebParam(name="startYearDate") @PathParam("startYearDate") String startYearDate, 
			@WebParam(name="endYearDate") @PathParam("endYearDate") String endYearDate, 
			@WebParam(name="frequency") @PathParam("frequency") String frequency, 
			@WebParam(name="dataFor") @PathParam("dataFor") String dataFor, 
			@WebParam(name="measureDataOrganizationId") @PathParam("measureDataOrganizationId") String measureDataOrganizationId, 
			@WebParam(name="measureDataEmployeeId") @PathParam("measureDataEmployeeId") String measureDataEmployeeId) throws Exception {
		
		HttpServletRequest request = messageContext.getHttpServletRequest();
		Subject subject = null;
		BscApiServiceResponse responseObj = new BscApiServiceResponse();
		responseObj.setSuccess( YesNo.NO );
		try {	
			subject = WsAuthenticateUtils.login();
			@SuppressWarnings("unchecked")
			IVisionService<VisionVO, BbVision, String> visionService = 
					(IVisionService<VisionVO, BbVision, String>) AppContext.getBean("bsc.service.VisionService");
			@SuppressWarnings("unchecked")
			IEmployeeService<EmployeeVO, BbEmployee, String> employeeService = 
					(IEmployeeService<EmployeeVO, BbEmployee, String>) AppContext.getBean("bsc.service.EmployeeService");
			@SuppressWarnings("unchecked")
			IOrganizationService<OrganizationVO, BbOrganization, String> organizationService = 
					(IOrganizationService<OrganizationVO, BbOrganization, String>) AppContext.getBean("bsc.service.OrganizationService");
			String visionOid = "";
			String measureDataOrganizationOid = "";
			String measureDataEmployeeOid = ""; 
			DefaultResult<VisionVO> visionResult = visionService.findForSimpleByVisId(visionId);
			if (visionResult.getValue() == null) {
				throw new Exception( visionResult.getSystemMessage().getValue() );
			}
			visionOid = visionResult.getValue().getOid();
			if (StringUtils.isBlank(measureDataOrganizationId)) {
				measureDataOrganizationOid = BscBaseLogicServiceCommonSupport
						.findEmployeeDataByEmpId(employeeService, measureDataOrganizationId)
						.getOid();
			}
			if (StringUtils.isBlank(measureDataEmployeeId)) {
				measureDataEmployeeOid = BscBaseLogicServiceCommonSupport
						.findOrganizationDataByUK(organizationService, measureDataEmployeeId)
						.getOid();
			}
			this.processForScorecard(
					responseObj, 
					request, 
					visionOid, startDate, endDate, startYearDate, endYearDate, frequency, dataFor, measureDataOrganizationOid, measureDataEmployeeOid);
		} catch (Exception e) {
			responseObj.setMessage( e.getMessage() );
		} finally {
			if (!YesNo.YES.equals(responseObj.getSuccess())) {
				responseObj.setMessage( SysMessageUtil.get(GreenStepSysMsgConstants.SEARCH_NO_DATA) );
			}	
			WsAuthenticateUtils.logout(subject);			
		}
		subject = null;
		return responseObj;
	}	
	
}