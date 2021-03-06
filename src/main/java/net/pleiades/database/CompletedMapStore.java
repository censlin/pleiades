/**
 * Pleiades
 * Copyright (C) 2011 - 2012
 * Computational Intelligence Research Group (CIRG@UP)
 * Department of Computer Science
 * University of Pretoria
 * South Africa
 */
package net.pleiades.database;

import com.hazelcast.core.MapStore;
import com.hazelcast.util.ConcurrentHashSet;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.WriteConcern;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.pleiades.persistence.PersistentCompletedMapObject;
import net.pleiades.simulations.CilibSimulation;
import net.pleiades.simulations.Simulation;

/**
 *
 * @author bennie
 */
public class CompletedMapStore implements MapStore<String, Simulation> {
    private static final String configFile = "pleiades.conf"; //fix this if you can
    private DBCollection results;

    public CompletedMapStore() {
        if (!connect()) {
            System.out.println(">ERROR: Unable to connect to persistent store. Contact administrator.");
            System.exit(1);
        }
        System.out.println(">[Connected to results map store]");
    }

    private boolean connect() {
        Properties properties = loadConfiguration();
        Mongo mongo;
        String storeAddress = properties.getProperty("persistent_store_address");
        int storePort = Integer.valueOf(properties.getProperty("persistent_store_port"));
        String pass = properties.getProperty("persistent_store_password");
        String user = properties.getProperty("persistent_store_user");
        boolean auth = false;
        
        try {
            mongo = new Mongo(storeAddress, storePort);
            mongo.setWriteConcern(WriteConcern.SAFE);
            DB db = mongo.getDB("Pleiades");
            auth = db.authenticate(user, pass.toCharArray());
            results = db.getCollection(properties.getProperty("completed_map"));
            results.setObjectClass(PersistentCompletedMapObject.class);
        } catch (Exception e) {
            return false;
        }
        
        return auth;
    }

    @Override
    public void store(String k, Simulation v) {
        DBObject o = new PersistentCompletedMapObject(v);
        BasicDBObject query = new BasicDBObject();

        query.put("id", o.get("id"));
        
        if (results.find(query).toArray().isEmpty()) {
            results.insert(o);
        } else {
            results.findAndModify(query, o);
        }
    }

    @Override
    public Simulation load(String k) {
        BasicDBObject query = new BasicDBObject();
        query.put("id", k);
        
        DBObject load = results.findOne(query);
        
        if (load == null) {
            return null;
        }
        
        Simulation s = new CilibSimulation((PersistentCompletedMapObject) load);
        
        return s;
    }

    @Override
    public void storeAll(Map<String, Simulation> map) {
        for (String k : map.keySet()) {
            store(k, map.get(k));
        }
    }

    @Override
    public void delete(String k) {
        BasicDBObject query = new BasicDBObject();
        query.put("id", k);
        
        results.remove(query);
    }

    @Override
    public void deleteAll(Collection<String> clctn) {
        for (String k : clctn) {
            delete(k);
        }
    }

    @Override
    public Map<String, Simulation> loadAll(Collection<String> clctn) {
        Map<String, Simulation> sims = new ConcurrentHashMap<String, Simulation>();
        
        for (String k : clctn) {
            sims.put(k, load(k));
        }
        
        return sims;
    }

    @Override
    public Set<String> loadAllKeys() {
        Set<String> keys = new ConcurrentHashSet<String>();
        BasicDBObject query = new BasicDBObject();
        
        DBCursor cursor = results.find(query);
        
        while (cursor.hasNext()) {
            keys.add((String) cursor.next().get("id"));
        }
        
        return keys;
    }
    
    private static Properties loadConfiguration() {
        Properties p = new Properties();
        
        try {
            p.load(new FileInputStream(configFile));
        } catch (IOException e) {
            throw new Error(">ERROR: Unable to load configuration file " + configFile);
        }
        
        return p;
    }
}
