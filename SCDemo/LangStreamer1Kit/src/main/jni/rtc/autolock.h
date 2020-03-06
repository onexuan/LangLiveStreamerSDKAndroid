//
// Created by lang on 2017/9/26.
//

#ifndef __AUTO_LOCK_H__
#define __AUTO_LOCK_H__

#include <pthread.h>

class CMutexLock
{
public:
    CMutexLock()
    {
        pthread_mutex_init(&m_lock, NULL);
    }

    ~CMutexLock()
    {
        pthread_mutex_destroy(&m_lock);
    }

    void lock()
    {
        pthread_mutex_lock(&m_lock);
    }

    void unlock()
    {
        pthread_mutex_unlock(&m_lock);
    }
private:
    pthread_mutex_t m_lock;
};

class CAutoLock
{
public:
    CAutoLock(CMutexLock& mutexLock):m_mutexLock(mutexLock)
    {
        m_mutexLock.lock();
    }

    ~CAutoLock()
    {
        m_mutexLock.unlock();
    }
private:
    CMutexLock& m_mutexLock;
};

#endif //__AUTO_LOCK_H__
