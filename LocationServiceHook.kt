/**
 * 这个hook主要用于排查小米系统的手机管家里显示APP频繁定位的问题。
 * 从app日志看应该没有那么多定位请求，故加此hook做一个排查，看看是否是第三方库引起。
 */
object LocationServiceHook {
    private const val TAG = "LocationServiceHook"
    @JvmStatic
    fun hook(){
        val serviceManager = Class.forName("android.os.ServiceManager")!!
        val getService = serviceManager.getDeclaredMethod("getService", String::class.java)!!
        val rawBinder = getService.invoke(null, Context.LOCATION_SERVICE) as IBinder

        val hookedBinder = Proxy.newProxyInstance(serviceManager.classLoader,
                arrayOf<Class<*>>(IBinder::class.java),
                LocationBinderProxyHookHandler(rawBinder)) as IBinder
        val cacheField = serviceManager.getDeclaredField("sCache")
        cacheField.isAccessible = true
        val caches = cacheField.get(null) as MutableMap<String, IBinder>
        caches[Context.LOCATION_SERVICE] = hookedBinder
        MyLog.logI(TAG, "finish hook")
    }
}

class LocationBinderProxyHookHandler(val base:IBinder):InvocationHandler{
    companion object{
        private const val TAG = "LocationBinderProxyHookHandler"
    }

    private val stub:Class<*>?
    private val iinterface:Class<*>?

    init {
        stub = try {
            Class.forName("android.location.ILocationManager\$Stub")
        }catch (throwable:Throwable){
            MyLog.logE(TAG, "android.location.ILocationManager", throwable)
            null
        }

        iinterface = try{
            Class.forName("android.location.ILocationManager")
        }catch (throwable:Throwable){
            MyLog.logE(TAG, "android.location.ILocationManager", throwable)
            null
        }
    }

    override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any {
        return if("queryLocalInterface" == method!!.name){
            MyLog.logI(TAG, "queryLocalInterface")
            Proxy.newProxyInstance(proxy!!.javaClass.classLoader,
                    arrayOf<Class<*>>(IBinder::class.java, IInterface::class.java, iinterface!!),
                    LocationBinderHookHandler(base, stub!!))
        }else {
            return if(args == null){
                method.invoke(base)
            }else{
                method.invoke(base, *args)
            }?:Unit
        }
    }

}

class LocationBinderHookHandler(base:IBinder, stubClass:Class<*> ):InvocationHandler{
    companion object{
        private const val TAG = "LocationBinderHookHandler"
    }

    private val base:Any?

    init {
        this.base = try {
            val asInterfaceMethod = stubClass.getDeclaredMethod("asInterface", IBinder::class.java)
            //ILocationManager.Stub.asInterface(base)
            asInterfaceMethod.invoke(null, base)
        }catch (throwable:Throwable){
            MyLog.logE(TAG, "", throwable)
            null
        }
    }

    override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any {
        MyLog.logI(TAG, "nethod: ${method!!.name}, args: $args", Throwable())
        return if(args == null){
            method.invoke(base)
        }else{
            method.invoke(base, *args)
        }?:Unit
    }
}
