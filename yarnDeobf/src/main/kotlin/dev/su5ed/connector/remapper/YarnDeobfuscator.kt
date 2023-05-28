package dev.su5ed.connector.remapper

import net.minecraftforge.gradle.common.tasks.JarExec
import net.minecraftforge.gradle.common.util.*
import net.minecraftforge.srgutils.IMappingFile
import net.minecraftforge.srgutils.INamedMappingFile
import org.gradle.api.Project
import org.w3c.dom.NodeList
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.util.*
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerConfigurationException
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathExpressionException
import javax.xml.xpath.XPathFactory

class YarnDeobfuscator(private val project: Project, private val cacheRoot: File) {
    private var xmlParser: DocumentBuilder? = null
    private var xPath: XPath? = null
    private var xmlTransformer: Transformer? = null

    init {
        try {
            xPath = XPathFactory.newInstance().newXPath()
            xmlParser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            xmlTransformer = TransformerFactory.newInstance().newTransformer()
        } catch (e: ParserConfigurationException) {
            throw RuntimeException("Error configuring XML parsers", e)
        } catch (e: TransformerConfigurationException) {
            throw RuntimeException("Error configuring XML parsers", e)
        }
    }

    @Throws(IOException::class)
    fun deobfPom(original: File, mappings: String, vararg cachePath: String): File {
        project.logger.debug("Updating POM file {} with mappings {}", original.name, mappings)
        val output = getCacheFile(*cachePath)
        val input = File(output.parent, output.name + ".input")
        val cache = HashStore()
            .load(input)
            .add("mappings", mappings)
            .add("orig", original)
        if (!cache.isSame || !output.exists()) {
            try {
                val pom = xmlParser!!.parse(original)
                val versionNodes = xPath!!.compile("/*[local-name()=\"project\"]/*[local-name()=\"version\"]").evaluate(pom, XPathConstants.NODESET) as NodeList
                if (versionNodes.length > 0) {
                    versionNodes.item(0).textContent = versionNodes.item(0).textContent + "_mapped_" + mappings
                }
                xmlTransformer!!.transform(DOMSource(pom), StreamResult(output))
            } catch (e: IOException) {
                project.logger.error("Error attempting to modify pom file " + original.name, e)
                return original
            } catch (e: SAXException) {
                project.logger.error("Error attempting to modify pom file " + original.name, e)
                return original
            } catch (e: XPathExpressionException) {
                project.logger.error("Error attempting to modify pom file " + original.name, e)
                return original
            } catch (e: TransformerException) {
                project.logger.error("Error attempting to modify pom file " + original.name, e)
                return original
            }
            Utils.updateHash(output, HashFunction.SHA1)
            cache.save()
        }
        return output
    }

    @Throws(IOException::class)
    fun deobfBinary(original: File, mappings: String?, vararg cachePath: String): File? {
        project.logger.debug("Deobfuscating binary file {} with mappings {}", original.name, mappings)
        val names = findMapping(mappings)
        if (names == null || !names.exists()) {
            return null
        }
        val output = getCacheFile(*cachePath)
        val input = File(output.parent, output.name + ".input")
        val cache = HashStore()
            .load(input)
            .add("names", names)
            .add("orig", original)
        if (!cache.isSame || !output.exists()) {
            val rename = project.tasks.create("_RenameIntermediary2Moj_" + Random().nextInt(), JarExec::class.java)
            rename.tool.set("net.minecraftforge:ForgeAutoRenamingTool:1.0.2:all")
            rename.args.addAll("--input", original.absolutePath, "--output", output.absolutePath, "--map", names.absolutePath, "--disable-abstract-param")
            rename.apply()
            rename.isEnabled = false
            Utils.updateHash(output, HashFunction.SHA1)
            cache.save()
        }
        return output
    }

    @Throws(IOException::class)
    fun deobfSources(original: File, mappings: String?, vararg cachePath: String): File? {
        return null
    }

    private fun getCacheFile(vararg cachePath: String): File {
        val cacheFile = File(cacheRoot, java.lang.String.join(File.separator, *cachePath))
        cacheFile.parentFile.mkdirs()
        return cacheFile
    }

    private fun findMapping(mapping: String?): File? {
        val desc = "yarn_1.19.4"
        val output = getCacheFile("intermediary_to_moj_1_19_4.tsrg")
        val input = File(output.parent, output.name + ".input")

        val cache = HashStore()
            .load(input)
            .add("names", desc)
        if (!cache.isSame || !output.exists()) {
            val yarn = MavenArtifactDownloader.generate(project, "net.fabricmc:yarn:1.19.4+build.2", true)!!
            val yarnMappings = ZipFile(yarn).use { zip ->
                val inputStream = zip.getInputStream(zip.getEntry("mappings/mappings.tiny"))
                INamedMappingFile.load(inputStream)
            }
            val obfToIntermediary = yarnMappings.getMap("official", "intermediary")

            val type = "client"
            val mappingsFile = MavenArtifactDownloader.generate(project, "net.minecraft:$type:1.19.4:mappings@txt", true)!!

            // Official is our "SRG" for MCPConfig-free environments
            val officialToObf = IMappingFile.load(mappingsFile)
            
            val intermediaryToMoj = officialToObf.chain(obfToIntermediary).reverse()
            
            intermediaryToMoj.write(output.toPath(), IMappingFile.Format.TSRG2, false)
        }
        return output
    }
}
