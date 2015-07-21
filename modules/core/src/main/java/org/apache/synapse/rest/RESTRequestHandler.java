/*
*  Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/

package org.apache.synapse.rest;

import org.apache.axis2.Constants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.aspects.ComponentType;
import org.apache.synapse.aspects.newstatistics.RuntimeStatisticCollector;
import org.apache.synapse.core.axis2.Axis2MessageContext;

import java.util.*;

/**
 * This class is responsible for receiving requests from various sources and dispatching
 * them to a suitable REST API for further processing. This is the main entry point for
 * mediating messages through APIs and Resources.
 */
public class RESTRequestHandler {

    private static final Log log = LogFactory.getLog(RESTRequestHandler.class);

    /**
     * Attempt to process the given message through one of the available APIs. This method
     * will first try to locate a suitable API for the given message by running it through
     * the API validation routines available. If a matching API is found it will dispatch
     * the message to the located API. If a matching API cannot be found, message will be
     * left intact so any other handlers (eg: main sequence) can pick it up later.
     *
     * @param synCtx MessageContext of the request to be processed
     * @return true if the message was dispatched to an API and false otherwise
     */
    public boolean process(MessageContext synCtx) {
        if (synCtx.isResponse()) {
            return dispatchToAPI(synCtx);
        }

        org.apache.axis2.context.MessageContext msgCtx = ((Axis2MessageContext) synCtx).
                getAxis2MessageContext();
        String protocol = msgCtx.getIncomingTransportName();
        if (!Constants.TRANSPORT_HTTP.equals(protocol) && !Constants.TRANSPORT_HTTPS.equals(protocol)) {
            if (log.isDebugEnabled()) {
                log.debug("Invalid protocol for REST API mediation: " + protocol);
            }
            return false;
        }

        return dispatchToAPI(synCtx);
    }

    private boolean dispatchToAPI(MessageContext synCtx) {
        Collection<API> apiSet = synCtx.getEnvironment().getSynapseConfiguration().getAPIs();
        //Since swapping elements are not possible with sets, Collection is converted to a List
        List<API> apiList=new ArrayList<API>(apiSet);

        API defaultAPI = null;
        int i=0;
        for(API api : apiList){
            if(api.getVersion().trim().equals("")){ //caught no version api
                //swap the no version api with the last element of the list to give it the lowest priority.
                Collections.swap(apiList,i,apiList.size()-1);

                if (log.isDebugEnabled()) {
                    log.debug("Found API: " + api.getName() + " which has no version. Moved it to the last position.");
                }
                break;
            }
            i++;
        }

        for (API api : apiList) {
            if ("/".equals(api.getContext())) {
                defaultAPI = api;
            } else if (api.canProcess(synCtx)) {
                if (log.isDebugEnabled()) {
                    log.debug("Located specific API: " + api.getName() + " for processing message");
                }
                apiProcess(synCtx, api);
                return true;
            }
        }

        if (defaultAPI != null && defaultAPI.canProcess(synCtx)) {
            apiProcess(synCtx, defaultAPI);
            return true;
        }

        return false;
    }

    private void apiProcess(MessageContext synCtx, API api) {
        if (!synCtx.isResponse()) {
            reportApiStartStatistics(synCtx, api);
        }
        api.process(synCtx);
    }

    private void reportApiStartStatistics(MessageContext synCtx, API api) {
        RuntimeStatisticCollector
                .recordStatisticCreateEntry(synCtx, api.getName(), ComponentType.API, "",
                                            System.currentTimeMillis());
    }
}
