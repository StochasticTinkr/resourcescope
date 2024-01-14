# ResourceScope

A simple library to help you manage resources in your application. This mimics the concepts of RAII in C++.

## Concepts

### Resource

A resource is something that can be acquired and released. This can be a file handle, a database connection, a network
connection, etc. Anything that needs to be acquired and released within a scope.

### ResourceScope

A resource scope is a collection of resources that are released when the scope is exited. The resources can be acquired
at any point within the scope and will be released when the scope is closed.

### Constructor

A constructor is a function that is used to acquire a resource. This function should prepare a reference to the resource
to be used. This function should not actually acquire the resource if it is also instantiating a wrapper object.  When
using a wrapper object, the resource should be acquired in the `then` function.  This ensures that the resource is not 
lost if the wrapper object can not be instantiated.

### Destructor

A destructor is a function that is used to release a resource. This function should release the resource.  This function
will be called automatically when the owning resource scope is closed.

## Example

```kotlin
import com.stochastictinkr.resourcescope.*
import java.io.FileReader
import org.lwjgl.glfw.GLFW.*

fun main() {
    resourceScope {
        // No need to hold on to the resource, it will be closed when the scope ends
        construct { glfwInit() } finally ::glfwTerminate
        
        val fileResource = constructClosable { FileReader("test.txt") }
        val file = fileResource.value
        val lines = file.readLines()
        // This could also have been written as:
        // val lines = constructClosable { FileReader("test.txt") }.value.readLines()
        // or as 
        // val (file, resource) = constructClosable { FileReader("test.txt") } // Destructuring for convenience
        // val lines = file.readLines()
    }
    // Resources are all closed at this point.
}
```



