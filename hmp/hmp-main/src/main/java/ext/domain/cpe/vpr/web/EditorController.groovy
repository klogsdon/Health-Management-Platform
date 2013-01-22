package EXT.DOMAIN.cpe.vpr.web

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import EXT.DOMAIN.cpe.auth.UserContext;
import EXT.DOMAIN.cpe.vista.rpc.RpcTemplate
import EXT.DOMAIN.cpe.vista.util.VistaStringUtils
import EXT.DOMAIN.cpe.vpr.Patient
import EXT.DOMAIN.cpe.vpr.PatientFacility
import EXT.DOMAIN.cpe.vpr.pom.AbstractPOMObject
import EXT.DOMAIN.cpe.vpr.pom.IPatientDAO

//import EXT.DOMAIN.cpe.vpr.queryeng.ViewDefRollup


import EXT.DOMAIN.cpe.vpr.UidUtils

import javax.servlet.http.HttpServletRequest

import org.springframework.beans.factory.annotation.Autowired

import org.springframework.stereotype.Controller

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam

import org.springframework.web.servlet.ModelAndView
import static EXT.DOMAIN.cpe.vpr.web.servlet.view.ModelAndViewFactory.contentNegotiatingModelAndView

import static EXT.DOMAIN.cpe.vpr.UserInterfaceRpcConstants.VPR_UI_CONTEXT
import EXT.DOMAIN.cpe.vpr.pom.jds.JdsGenericPatientObjectDAO

import static EXT.DOMAIN.cpe.vpr.web.servlet.view.ModelAndViewFactory.stringModelAndView

@Controller
@RequestMapping("/editor/**")
class EditorController {
	
	@Autowired JdsGenericPatientObjectDAO genericJdsDAO
	
	@Autowired
	RpcTemplate rpcTemplate
	
	@Autowired UserContext userContext
	
    @Autowired
    IPatientDAO patientDao
	
	// TODO: How to handle  board column data, maybe when there is no data or no record? Submitting NEW data?
	@RequestMapping(value = "submitFieldValue", method = RequestMethod.POST)
	ModelAndView submitJdsFieldValue(
		@RequestParam(required = true) String uid,
		@RequestParam(required = true) String fieldName,
		@RequestParam(required = true) Object value,
		@RequestParam(required = true) String pid,
		HttpServletRequest request) 
	{
		
		AbstractPOMObject obj = genericJdsDAO.findByUID(uid);
		obj.setData(fieldName, value);
        Map tmp = obj.data
        tmp.remove('pid')
		/*
		 * Now we have an updated object; Can we send this to Vista?
		 * Assume new HMP data
		 * Will need to filter on facility code at some point. UI should not allow editing this, but
		 * just in case, we can dbl check here to make sure.
		 */
		
		String fcode = userContext.currentUser.getDivision()
		String type = UidUtils.getVistaClassNameByUid(uid);
		
		String dfn = null;
		
		String rpcResult = "";
		
		Patient pt = patientDao.findByVprPid(pid)
		if(pt!=null) {
			for(PatientFacility fc: pt.getFacilities()) {
				if(fc.code.equals(fcode)) {
					dfn = fc.localPatientId;
				}
			}
			if(dfn!=null) {
            String json = EXT.DOMAIN.cpe.vpr.pom.POMUtils.toJSON(tmp)
			def submitValue = VistaStringUtils.splitLargeStringIfNecessary(json)
			rpcResult = rpcTemplate.executeForString("/${VPR_UI_CONTEXT}/VPR PUT PATIENT DATA", dfn, type, submitValue)
			ObjectMapper jsonMapper = new ObjectMapper();
			JsonNode result = jsonMapper.readValue(rpcResult, JsonNode.class)
			if (result.path("success")) {
//				JsonNode dataNode = result.path("data")
	
				genericJdsDAO.save(obj)
			}
			}
		} else {
			throw new PatientNotFoundException(pid)
		}
        return stringModelAndView(rpcResult, "application/json");
	}
	
	@RequestMapping(value = "submitVistaData", method = RequestMethod.GET)
	ModelAndView submitVistaData(
		@RequestParam(required = true) String uid,
		@RequestParam(required = true) String fieldName,
		@RequestParam(required = true) Object value,
		HttpServletRequest request)
	{
		
		AbstractPOMObject obj = genericJdsDAO.findByUID(uid);
		obj.setData(fieldName, value);
		genericJdsDAO.save(obj);
		return contentNegotiatingModelAndView(obj);
	}
}
