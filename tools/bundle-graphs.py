#! /usr/bin/env python
import re
from sets import Set
import os
import pygraphviz as pgv
import sys
import telnetlib

"""
Builds a graph of bundle requirements (dependencies) of org.sakaiproject
bundles from a running server by querying over telnet. The graph is used
to seed pygraphviz for producing a graph of the overall system, graphs
for each bundle's successors and each bundle's predecessors.

Script dependencies
-------------------
* pygraphviz

Server dependencies
-------------------
* Felix Remote Shell
** Felix Shell (Felix Remote Shell dependency)

"""

# [   1] [Active     ] [   15] org.sakaiproject.nakamura.messaging (0.11.0.SNAPSHOT)
bundle_from_ps = re.compile('^\[\s*(?P<bundle_id>\d+)\]\s\[.+\]\s(?P<bundle_name>.+)\s')

# org.sakaiproject.nakamura.api.solr; version=0.0.0 -> org.sakaiproject.nakamura.solr [11]
bundle_from_req = re.compile('^.*-\> (?P<bundle_name>.*) \[(?P<bundle_id>.*)\]$')

def get_sakai_bundles():
    """
    Get a list of bundles that are create as part of Sakai OAE.
    Returns a dictionary of dict[bundle_name] = bundle_id.
    
    """
    tn = telnetlib.Telnet('localhost', '6666')
    tn.write('ps -s\nexit\n')
    lines = [line for line in tn.read_all().split('\r\n') if 'org.sakaiproject' in line]

    bundles = {}
    for line in lines:
        m = bundle_from_ps.match(line)
        bundles[m.group('bundle_name')] = m.group('bundle_id')
    return bundles

def get_package_reqs(bundle_id):
    """
    Gets the requirements (imports) for a given bundle.
    Returns a dictionary of dict[bundle_name] = bundle_id.
    
    Keyword arguments:
    bundle_id -- Bundle ID returned by the server in the output of
                 get_sakai_bundles()
    
    """
    tn = telnetlib.Telnet('localhost', '6666')
    tn.write('inspect package requirement %s\nexit\n' % (bundle_id))
    lines = [line for line in tn.read_all().split('\r\n') if line.startswith('org.sakaiproject') and line.endswith(']')]

    reqs = {}
    for line in lines:
        m = bundle_from_req.match(line)
        reqs[m.group('bundle_name')] = m.group('bundle_id')
    return reqs

def build_bundle_graph():
    """
    Build a graph_attr (nodes, edges) representing the connectivity
    within Sakai bundles
    
    """
    sakai_bundles = get_sakai_bundles()
    bundles = {}
    for b_name, b_id in sakai_bundles.items():
        reqs = get_package_reqs(b_id)
        bundles[b_name] = reqs.keys()
    return bundles

def draw_subgraph(name, graph, filename, successors = True):
    """
    Draw (write to disk) a subgraph that starts with or ends with
    the specified node.
    
    Keyword arguments:
    name -- name of the node to focus on
    graph -- graph of paths between bundles
    filename -- filename to write subgraph to
    successors -- whether to lookup successors or predecessors
    
    """
    if successors:
        nbunch = graph.successors(name)
    else:
        nbunch = graph.predecessors(name)

    if nbunch:
        nbunch.append(name)
        subgraph = graph.subgraph(nbunch)
        subgraph.layout(prog = 'dot')
        subgraph.draw('graphviz/%s' % filename)

def main():
    if not os.path.isdir('graphviz'):
        os.mkdir('graphviz')

    bundles_graph = build_bundle_graph()

    # print the whole graph
    graph = pgv.AGraph(data = bundles_graph, directed = True)
    graph.layout(prog = 'dot')
    graph.draw('graphviz/org.sakaiproject.nakamura.png')

    for b_name, reqs in bundles_graph.items():
        draw_subgraph(b_name, graph, '%s.png' % b_name)
        draw_subgraph(b_name, graph, '%s-pred.png' % b_name, False)

if __name__ == '__main__':
    main()

