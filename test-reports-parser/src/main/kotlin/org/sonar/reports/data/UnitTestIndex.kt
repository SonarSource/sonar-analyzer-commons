package org.sonar.reports.data

class UnitTestIndex(private val indexByClassname: MutableMap<String, UnitTestClassReport> = mutableMapOf()) {

    fun index(classname: String): UnitTestClassReport {
        return indexByClassname.computeIfAbsent(classname) { UnitTestClassReport() }
    }

    operator fun get(classname: String): UnitTestClassReport? {
        return indexByClassname[classname]
    }

    fun getClassnames(): Set<String> {
        return HashSet(indexByClassname.keys)
    }

    fun size(): Int {
        return indexByClassname.size
    }

    fun merge(classname: String, intoClassname: String): UnitTestClassReport? =
        indexByClassname[classname]?.let {
            val to = index(intoClassname)
            to.add(it)
            indexByClassname.remove(classname)
            return to
        }

    fun remove(classname: String) = indexByClassname.remove(classname)
}