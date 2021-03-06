package org.osehra.cpe.vpr.web

import org.osehra.cpe.vpr.RosterService;
import org.osehra.cpe.vpr.sync.ISyncService
import org.springframework.beans.factory.annotation.Autowired;
import org.osehra.cpe.auth.UserContext
import org.osehra.cpe.jsonc.JsonCResponse
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView

import static org.osehra.cpe.vpr.web.servlet.view.ModelAndViewFactory.contentNegotiatingModelAndView
import static org.springframework.web.bind.annotation.RequestMethod.GET

@Controller
class PatientSelectorController {
	@Autowired
	UserContext userContext
	
	@Autowired
	ISyncService syncService
	
	@Autowired
	RosterService rosterService
	
	@RequestMapping(value = '/patientSelector/select', method = GET)
	ModelAndView addSelectedPatient(@RequestParam(required = true) String dfn, HttpServletRequest request) {
		def	message = null;
		if (!dfn) throw new BadRequestException("'dfn' request parameter is required")
		
		String vistaId = userContext.currentUser.vistaId
		def rosterId = request.session.getAttribute('rosterID')
		
		if(!rosterId){
			message = "Can't identify current roster, please make a selection."
		}else{
			rosterService.addPatientToRoster(dfn, rosterId)		
			syncService.sendLoadPatientMsgWithDfn(vistaId, dfn)
			message= sprintf("Loading patient :%1s;%2s...",[vistaId,dfn])
		}
		
		return contentNegotiatingModelAndView(JsonCResponse.create(request, [message: message]));
	}
	
}
