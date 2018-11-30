package ru.otus.dbService;


import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.stereotype.Component;
import ru.otus.cache.CacheElement;
import ru.otus.cache.CacheEngine;
import ru.otus.dataBase.DBConnection;
import ru.otus.dataSets.DataSet;
import ru.otus.messageSystem.Address;
import ru.otus.messageSystem.Addressee;
import ru.otus.messageSystem.MessageSystem;
import ru.otus.messageSystem.app.MessageSystemContext;

import java.util.List;
import java.util.function.Function;

@Component
public class DBServiceImpl implements DBService, Addressee {
    private final DBConnection factory;
    private final CacheEngine cache;
    private final Address address;
    private final MessageSystemContext context;
    public DBServiceImpl(CacheEngine cache, Address address,MessageSystemContext context) {
        this.factory = new DBConnection();
        this.cache = cache;
        this.address=address;
        this.context=context;
        this.context.getMessageSystem().addAddressee(this);
        this.context.setDbAddress(address);
    }

    /*public DBServiceImpl(DBConnection connection,CacheEngine cache) {
        this.factory = connection;
        this.cache = cache;
    }*/
    @Override
    public Address getAddress(){
        return this.address;
    }
    @Override
    public MessageSystem getMS(){
        return context.getMessageSystem();
    };
    public SessionFactory getConnection() {
        return this.factory.getFactory();
    }

    public CacheEngine getCache() {
        return cache;
    }

    @Override
    public String getLocalStatus() {
        return null;
    }

    @Override
    public <T extends DataSet> void save(T dataSet) {
        try (Session session = getConnection().openSession()) {
            UserDataSetDAO dao = new UserDataSetDAO(session);
            dao.save(dataSet);
            CacheElement<T> elem = new CacheElement<>(dataSet);
            cache.put(elem);
        }
    }

    private <R> R runInSession(Function<Session, R> function) {
        try (Session session = getConnection().openSession()) {
            Transaction transaction = session.beginTransaction();
            R result = function.apply(session);
            transaction.commit();
            return result;
        }
    }

    @Override
    public <T extends DataSet> T read(long id, Class<T> clazz) {
        CacheElement<T> cacheElem = (CacheElement<T>) this.cache.get(id);
        if (cacheElem != null) {
            return cacheElem.getObj();
        }
        return runInSession(session -> {
            UserDataSetDAO dao = new UserDataSetDAO(session);
            return dao.read(id, clazz);
        });
    }

    @Override
    public <T extends DataSet> T readByName(String name, Class<T> clazz) {
        return runInSession(session -> {
            UserDataSetDAO dao = new UserDataSetDAO(session);
            return dao.readByName(name, clazz);
        });
    }

    @Override
    public <T extends DataSet> List<T> readAll(Class<T> clazz) {
        return runInSession(session -> {
            UserDataSetDAO dao = new UserDataSetDAO(session);
            return dao.readAll(clazz);
        });
    }

    @Override
    public <T extends DataSet> void addUsers(List<T> users) {
        try (Session session = getConnection().openSession()) {
            for (DataSet user : users) {
                //session.save(user);
                UserDataSetDAO dao = new UserDataSetDAO(session);
                dao.save(user);
                CacheElement<T> elem = new CacheElement<>((T) user);
                cache.put(elem);
            }
        }
    }

    @Override
    public <T extends DataSet> T loadUser(long id, Class<T> clazz) {
        CacheElement<T> cacheElem = (CacheElement<T>) this.cache.get(id);
        if (cacheElem != null) {
            return cacheElem.getObj();
        }
        return runInSession(session -> {
            UserDataSetDAO dao = new UserDataSetDAO(session);
            return dao.read(id, clazz);
        });
    }

    @Override
    public void shutdown() {
        this.factory.close();
        cache.dispose();
    }

    @Override
    public void close() throws Exception {
        shutdown();
    }
}
