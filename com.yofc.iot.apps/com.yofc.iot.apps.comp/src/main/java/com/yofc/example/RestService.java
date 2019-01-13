package com.yofc.example;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true, service = RestService.class)
@Path("/rod")
public class RestService {

    private static final Logger logger = LoggerFactory.getLogger(RestService.class);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> listAssetRods() {
        List<String> list = new ArrayList<String>();
        list.add("aaa");
        list.add("bbbbbb");
        return list;
    }

    @Activate
    public void start(final Map<String, Object> properties) {
        dumpProperties("start", properties);
        logger.info("start");
    }

    @Deactivate
    public void deactivate() {
        System.out.println("deactivate");
    }

    private void dumpProperties(String operation, final Map<String, Object> properties) {
        final Map<String, Object> sortedMap = new TreeMap<>(properties);

        System.out.format("=========== %s ===========%n", operation);
        for (Map.Entry<String, Object> entry : sortedMap.entrySet()) {
            System.out.format("\t'%s' = '%s'%n", entry.getKey(), entry.getValue());
        }
        System.out.format("=========== %s ===========%n", operation);
    }
}
