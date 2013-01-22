package EXT.DOMAIN.cpe.vpr.web.servlet;

import grails.util.GrailsUtil;
import org.codehaus.groovy.grails.commons.ApplicationAttributes;
import org.codehaus.groovy.grails.commons.GrailsApplication;
import org.codehaus.groovy.grails.web.context.ServletContextHolder;
import org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes;
import org.codehaus.groovy.grails.web.servlet.WrappedResponseHolder;
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsWebRequest;
import org.codehaus.groovy.grails.web.util.WebUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.util.Assert;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.mvc.annotation.AnnotationMethodHandlerAdapter;
import org.springframework.web.util.NestedServletException;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Locale;
import java.util.Map;

public class GrailsDispatcherServlet extends DispatcherServlet {

    private static final String EXCEPTION_ATTRIBUTE = "exception";
    protected MultipartResolver multipartResolver;
    private LocaleResolver localeResolver;

    @Override
    protected void initFrameworkServlet() throws ServletException, BeansException {
        super.initFrameworkServlet();
        initMultipartResolver();
    }

    /**
     * Initialize the MultipartResolver used by this class.
     * If no bean is defined with the given name in the BeanFactory
     * for this namespace, no multipart handling is provided.
     *
     * @throws org.springframework.beans.BeansException
     *          Thrown if there is an error initializing the mutlipartResolver
     */
    private void initMultipartResolver() throws BeansException {
        try {
            multipartResolver = getWebApplicationContext().getBean(MULTIPART_RESOLVER_BEAN_NAME, MultipartResolver.class);
            if (logger.isInfoEnabled()) {
                logger.info("Using MultipartResolver [" + multipartResolver + "]");
            }
        } catch (NoSuchBeanDefinitionException ex) {
            // Default is no multipart resolver.
            multipartResolver = null;
            if (logger.isInfoEnabled()) {
                logger.info("Unable to locate MultipartResolver with name '" + MULTIPART_RESOLVER_BEAN_NAME +
                        "': no multipart request handling provided");
            }
        }
    }

    @Override
    protected void initStrategies(ApplicationContext context) {
        super.initStrategies(context);
        initLocaleResolver(context);
    }

    // copied from base class since it's private
    private void initLocaleResolver(ApplicationContext context) {
        try {
            localeResolver = context.getBean(LOCALE_RESOLVER_BEAN_NAME, LocaleResolver.class);
            if (logger.isDebugEnabled()) {
                logger.debug("Using LocaleResolver [" + localeResolver + "]");
            }
        } catch (NoSuchBeanDefinitionException ex) {
            // We need to use the default.
            localeResolver = getDefaultStrategy(context, LocaleResolver.class);
            if (logger.isDebugEnabled()) {
                logger.debug("Unable to locate LocaleResolver with name '" + LOCALE_RESOLVER_BEAN_NAME +
                        "': using default [" + localeResolver + "]");
            }
        }
    }

    @Override
    protected WebApplicationContext createWebApplicationContext(ApplicationContext parent) {
        WebApplicationContext wac = super.createWebApplicationContext(parent);

        GrailsApplication application = parent.getBean(GrailsApplication.APPLICATION_ID, GrailsApplication.class);
        Assert.notNull(application, "grailsApplication bean not found");

        configureServletContextAttributes(getServletContext(), application, wac);
        return wac;
    }

    @Override
    protected void doDispatch(final HttpServletRequest request, HttpServletResponse response) throws Exception {

        request.setAttribute(LOCALE_RESOLVER_ATTRIBUTE, localeResolver);

        HttpServletRequest processedRequest = request;
        HandlerExecutionChain mappedHandler = null;
        int interceptorIndex = -1;

        // Expose current LocaleResolver and request as LocaleContext.
        LocaleContext previousLocaleContext = LocaleContextHolder.getLocaleContext();
        LocaleContextHolder.setLocaleContext(new LocaleContext() {
            public Locale getLocale() {
                return localeResolver.resolveLocale(request);
            }
        });

        // If the request is an include we need to try to use the original wrapped sitemesh
        // response, otherwise layouts won't work properly
        if (WebUtils.isIncludeRequest(request)) {
            response = useWrappedOrOriginalResponse(response);
        }

        GrailsWebRequest requestAttributes = null;
        GrailsWebRequest previousRequestAttributes = null;
        Exception handlerException = null;
        try {
            ModelAndView mv;
            boolean errorView = false;
            try {
                Object exceptionAttribute = request.getAttribute(EXCEPTION_ATTRIBUTE);
                // only process multipart requests if an exception hasn't occured
                if (exceptionAttribute == null) {
                    processedRequest = checkMultipart(request);
                }
                // Expose current RequestAttributes to current thread.
                previousRequestAttributes = (GrailsWebRequest) RequestContextHolder.currentRequestAttributes();
                requestAttributes = new GrailsWebRequest(processedRequest, response, getServletContext());
                copyParamsFromPreviousRequest(previousRequestAttributes, requestAttributes);

                // Update the current web request.
                WebUtils.storeGrailsWebRequest(requestAttributes);

                if (logger.isDebugEnabled()) {
                    logger.debug("Bound request context to thread: " + request);
                    logger.debug("Using response object: " + response.getClass());
                }

                // Determine handler for the current request.
                mappedHandler = getHandler(processedRequest, false);
                if (mappedHandler == null || mappedHandler.getHandler() == null) {
                    noHandlerFound(processedRequest, response);
                    return;
                }

                // Apply preHandle methods of registered interceptors.
                if (mappedHandler.getInterceptors() != null) {
                    for (int i = 0; i < mappedHandler.getInterceptors().length; i++) {
                        HandlerInterceptor interceptor = mappedHandler.getInterceptors()[i];
                        if (!interceptor.preHandle(processedRequest, response, mappedHandler.getHandler())) {
                            triggerAfterCompletion(mappedHandler, interceptorIndex, processedRequest, response, null);
                            return;
                        }
                        interceptorIndex = i;
                    }
                }

                // Actually invoke the handler.
                HandlerAdapter ha = getHandlerAdapter(mappedHandler.getHandler());
                mv = ha.handle(processedRequest, response, mappedHandler.getHandler());

                // Do we need view name translation?
                if ((ha instanceof AnnotationMethodHandlerAdapter) && mv != null && !mv.hasView()) {
                    mv.setViewName(getDefaultViewName(request));
                }

                // Apply postHandle methods of registered interceptors.
                if (mappedHandler.getInterceptors() != null) {
                    for (int i = mappedHandler.getInterceptors().length - 1; i >= 0; i--) {
                        HandlerInterceptor interceptor = mappedHandler.getInterceptors()[i];
                        interceptor.postHandle(processedRequest, response, mappedHandler.getHandler(), mv);
                    }
                }
            } catch (ModelAndViewDefiningException ex) {
                GrailsUtil.deepSanitize(ex);
                handlerException = ex;
                if (logger.isDebugEnabled()) {
                    logger.debug("ModelAndViewDefiningException encountered", ex);
                }
                mv = ex.getModelAndView();
            } catch (Exception ex) {
                GrailsUtil.deepSanitize(ex);
                handlerException = ex;
                Object handler = (mappedHandler != null ? mappedHandler.getHandler() : null);
                mv = processHandlerException(request, response, handler, ex);
                errorView = (mv != null);
            }

            // Did the handler return a view to render?
            if (mv != null && !mv.wasCleared()) {
                // If an exception occurs in here, like a bad closing tag,
                // we have nothing to render.

                try {
                    render(mv, processedRequest, response);
                    if (errorView) {
                        WebUtils.clearErrorRequestAttributes(request);
                    }
                } catch (Exception e) {
                    // Only render the error view if we're not already trying to render it.
                    // This prevents a recursion if the error page itself has errors.
                    if (request.getAttribute(GrailsApplicationAttributes.RENDERING_ERROR_ATTRIBUTE) == null) {
                        request.setAttribute(GrailsApplicationAttributes.RENDERING_ERROR_ATTRIBUTE, Boolean.TRUE);

                        mv = super.processHandlerException(processedRequest, response, mappedHandler, e);
                        handlerException = e;
                        if (mv != null) render(mv, processedRequest, response);
                    } else {
                        request.removeAttribute(GrailsApplicationAttributes.RENDERING_ERROR_ATTRIBUTE);
                        logger.warn("Recursive rendering of error view detected.", e);

                        try {
                            response.setContentType("text/plain");
                            response.getWriter().write("Internal server error");
                            response.flushBuffer();
                        } catch (Exception e2) {
                            logger.error("Internal server error - problem rendering error view", e2);
                        }

                        requestAttributes.setRenderView(false);
                        return;
                    }
                }
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("Null ModelAndView returned to DispatcherServlet with name '" +
                            getServletName() + "': assuming HandlerAdapter completed request handling");
                }
            }

            // Trigger after-completion for successful outcome.
            triggerAfterCompletion(mappedHandler, interceptorIndex, processedRequest, response, handlerException);
        } catch (Exception ex) {
            // Trigger after-completion for thrown exception.
            triggerAfterCompletion(mappedHandler, interceptorIndex, processedRequest, response, ex);
            throw ex;
        } catch (Error err) {
            ServletException ex = new NestedServletException("Handler processing failed", err);
            // Trigger after-completion for thrown exception.
            triggerAfterCompletion(mappedHandler, interceptorIndex, processedRequest, response, ex);
            throw ex;
        } finally {
            // Clean up any resources used by a multipart request.
            if (processedRequest instanceof MultipartHttpServletRequest && processedRequest != request) {
                if (multipartResolver != null) {
                    multipartResolver.cleanupMultipart((MultipartHttpServletRequest) processedRequest);
                }
            }
            request.removeAttribute(MultipartHttpServletRequest.class.getName());

            // Reset thread-bound holders
            if (requestAttributes != null) {
                requestAttributes.requestCompleted();
                WebUtils.storeGrailsWebRequest(previousRequestAttributes);
            }

            LocaleContextHolder.setLocaleContext(previousLocaleContext);

            if (logger.isDebugEnabled()) {
                logger.debug("Cleared thread-bound request context: " + request);
            }
        }
    }

    protected HttpServletResponse useWrappedOrOriginalResponse(HttpServletResponse response) {
        HttpServletResponse r = WrappedResponseHolder.getWrappedResponse();
        if (r != null) return r;
        return response;
    }

    protected void copyParamsFromPreviousRequest(GrailsWebRequest previousRequestAttributes, GrailsWebRequest requestAttributes) {
        Map previousParams = previousRequestAttributes.getParams();
        Map params = requestAttributes.getParams();
        for (Object o : previousParams.keySet()) {
            String name = (String) o;
            params.put(name, previousParams.get(name));
        }
    }

    /**
     * Trigger afterCompletion callbacks on the mapped HandlerInterceptors.
     * Will just invoke afterCompletion for all interceptors whose preHandle
     * invocation has successfully completed and returned true.
     *
     * @param mappedHandler    the mapped HandlerExecutionChain
     * @param interceptorIndex index of last interceptor that successfully completed
     * @param ex               Exception thrown on handler execution, or <code>null</code> if none
     * @see HandlerInterceptor#afterCompletion
     */
    protected void triggerAfterCompletion(
            HandlerExecutionChain mappedHandler, int interceptorIndex,
            HttpServletRequest request, HttpServletResponse response, Exception ex) throws Exception {

        if (mappedHandler == null || mappedHandler.getInterceptors() == null) {
            return;
        }

        // Apply afterCompletion methods of registered interceptors.
        for (int i = interceptorIndex; i >= 0; i--) {
            HandlerInterceptor interceptor = mappedHandler.getInterceptors()[i];
            try {
                interceptor.afterCompletion(request, response, mappedHandler.getHandler(), ex);
            } catch (Throwable e) {
                GrailsUtil.deepSanitize(e);
                logger.error("HandlerInterceptor.afterCompletion threw exception", e);
            }
        }
    }

    public static void configureServletContextAttributes(ServletContext servletContext, GrailsApplication application, WebApplicationContext webContext) {
        ServletContextHolder.setServletContext(servletContext);

        // use config file locations if available
        servletContext.setAttribute(ApplicationAttributes.PARENT_APPLICATION_CONTEXT, webContext.getParent());
        servletContext.setAttribute(GrailsApplication.APPLICATION_ID, application);

        servletContext.setAttribute(ApplicationAttributes.APPLICATION_CONTEXT, webContext);
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, webContext);
    }

    /**
     * Convert the request into a multipart request.
     * If no multipart resolver is set, simply use the existing request.
     * @param request current HTTP request
     * @return the processed request (multipart wrapper if necessary)
     */
    @Override
    protected HttpServletRequest checkMultipart(HttpServletRequest request) throws MultipartException {
        // Lookup from request attribute. The resolver that handles MultiPartRequest is dealt with earlier inside DefaultUrlMappingInfo with Grails
        HttpServletRequest resolvedRequest = (HttpServletRequest) request.getAttribute(MultipartHttpServletRequest.class.getName());
        if (resolvedRequest != null) return resolvedRequest;
        return request;
    }

    @Override
    public HandlerExecutionChain getHandler(HttpServletRequest request, boolean cache) throws Exception {
        return super.getHandler(request, cache);
    }
}
