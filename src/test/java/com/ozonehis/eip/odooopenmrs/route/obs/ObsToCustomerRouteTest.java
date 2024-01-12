package com.ozonehis.eip.odooopenmrs.route.obs;

import static java.util.Collections.singletonMap;
import static org.openmrs.eip.mysql.watcher.WatcherConstants.PROP_EVENT;
import static org.springframework.test.context.support.TestPropertySourceUtils.addInlinedPropertiesToEnvironment;

import ch.qos.logback.classic.Level;
import com.ozonehis.eip.odooopenmrs.route.BaseOdooRouteTest;
import com.ozonehis.eip.odooopenmrs.route.OdooTestConstants;
import java.util.HashMap;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.eip.mysql.watcher.Event;

public class ObsToCustomerRouteTest extends BaseOdooRouteTest {

    private static final String URI_TEST_RULE = "mock:test-rule";

    private static final String TABLE = "obs";

    private static final String OBS_UUID = "obs-uuid-1";

    private static final String PATIENT_UUID = "patient-uuid";

    private static final String PROP_DECISION_RULE = "obs.to.customer.decision.rule.endpoint";

    private static final String EX_PROP_SKIP_CUSTOMER_UPDATE = "skipCustomerUpdate";

    @EndpointInject("mock:patient-uuid-to-odoo-customer")
    private MockEndpoint mockPatientUuidToCustomerEndpoint;

    @EndpointInject(URI_TEST_RULE)
    private MockEndpoint mockTestRuleEndpoint;

    @BeforeEach
    public void setup() throws Exception {
        loadXmlRoutesInCamelDirectory("obs/obs-to-customer.xml");

        mockTestRuleEndpoint.reset();
        mockPatientUuidToCustomerEndpoint.reset();

        advise(OdooTestConstants.ROUTE_ID_OBS_TO_CUSTOMER, new AdviceWithRouteBuilder() {

            @Override
            public void configure() {
                interceptSendToEndpoint(OdooTestConstants.URI_PATIENT_UUID_TO_CUSTOMER)
                        .skipSendToOriginalEndpoint()
                        .to(mockPatientUuidToCustomerEndpoint);
            }
        });
    }

    @Test
    public void shouldSkipAnObsThatFailsTheDecisionRule() throws Exception {
        addInlinedPropertiesToEnvironment(env, PROP_DECISION_RULE + "=" + URI_TEST_RULE);
        Exchange exchange = new DefaultExchange(camelContext);
        Event event = createEvent(TABLE, "1", OBS_UUID, "c");
        exchange.setProperty(PROP_EVENT, event);
        var obsResource = new HashMap<>();
        obsResource.put("uuid", OBS_UUID);
        var patientResource = new HashMap<>();
        patientResource.put("uuid", PATIENT_UUID);
        obsResource.put("person", patientResource);
        exchange.setProperty(OdooTestConstants.EX_PROP_ENTITY, obsResource);
        mockPatientUuidToCustomerEndpoint.expectedMessageCount(0);
        mockTestRuleEndpoint.expectedMessageCount(1);
        mockTestRuleEndpoint.expectedBodiesReceived(obsResource);
        mockTestRuleEndpoint.whenAnyExchangeReceived(e -> e.getIn().setBody(false));

        producerTemplate.send(OdooTestConstants.URI_OBS_TO_CUSTOMER, exchange);

        mockPatientUuidToCustomerEndpoint.assertIsSatisfied();
        mockTestRuleEndpoint.assertIsSatisfied();
        mockTestRuleEndpoint.expectedBodyReceived();
        assertMessageLogged(
                Level.INFO, "Skipping obs event because it failed the decision rules defined in -> " + URI_TEST_RULE);
    }

    @Test
    public void shouldProcessAnObsThatPassesTheDecisionRule() throws Exception {
        addInlinedPropertiesToEnvironment(env, PROP_DECISION_RULE + "=" + URI_TEST_RULE);
        Exchange exchange = new DefaultExchange(camelContext);
        Event event = createEvent(TABLE, "1", OBS_UUID, "c");
        exchange.setProperty(PROP_EVENT, event);
        var obsResource = new HashMap<>();
        obsResource.put("uuid", OBS_UUID);
        obsResource.put("person", singletonMap("uuid", PATIENT_UUID));
        exchange.setProperty(OdooTestConstants.EX_PROP_ENTITY, obsResource);
        mockPatientUuidToCustomerEndpoint.expectedMessageCount(1);
        mockPatientUuidToCustomerEndpoint.expectedPropertyReceived(EX_PROP_SKIP_CUSTOMER_UPDATE, true);
        mockPatientUuidToCustomerEndpoint.expectedBodiesReceived(PATIENT_UUID);
        mockTestRuleEndpoint.expectedMessageCount(1);
        mockTestRuleEndpoint.expectedBodiesReceived(obsResource);
        mockTestRuleEndpoint.whenAnyExchangeReceived(e -> e.getIn().setBody(true));

        producerTemplate.send(OdooTestConstants.URI_OBS_TO_CUSTOMER, exchange);

        mockPatientUuidToCustomerEndpoint.assertIsSatisfied();
        mockPatientUuidToCustomerEndpoint.expectedBodyReceived();
        mockTestRuleEndpoint.assertIsSatisfied();
        mockTestRuleEndpoint.expectedBodyReceived();
    }
}
