# 云文件系统
将文件读和文件写分开，有读网关和写网关，分别有各自的RESTFull API

# reader server api:
get /?list=/users/002838388333/ http/1.0
意为列出path目录下的文件和文件夹,如果无权限则被拒绝
返回：
{
 "state":200,
 "message":"ok",
 "dataText":{"/users/002838388333/a":"d","/users/002838388333/b":"d","/users/002838388333/1.txt":"f",},
}
其中d表示目录，f表示文件