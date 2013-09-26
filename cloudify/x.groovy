import java.util.concurrent.TimeUnit
import groovy.text.SimpleTemplateEngine
import groovy.util.ConfigSlurper;
import java.net.InetAddress;


def targets=["a","b","c"]
def pu = new File('pu')
def lu1 = ["gwname":"NY","address":"123.2.3.1","discoport":10000,"commport":10001]
def engine = new SimpleTemplateEngine()
def binding=["targets":targets,"localgwname":"BLORF","localspaceurl":"jini://*/*/space","lookups":[lu1],"sources":["A","B"]]
def template = engine.createTemplate(pu).make(binding)

println template.toString()
