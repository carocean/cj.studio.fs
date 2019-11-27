$(document).ready(function(){
    var uploader = easyUploader({
        id: "uploader",
        accept: '*.*',
        action: '/upload/uploader.service',
        dataFormat: 'formData',
        maxCount: 10000000,
        maxSize: 300000000,
        multiple: true,
        data: null,
        onFetchUrl:function(action){
            var currentdir=$('div[currentdir]').attr('currentdir');
            var dir=action+'?dir='+currentdir;
            console.log('-----'+dir);
            return dir;
        },
        beforeUpload: function (file, data, args) {
            /* dataFormat为formData时配置发送数据的方式 */
            // data.append('token', '387126b0-7b3e-4a2a-86ad-ae5c5edd0ae6TT');
            // data.append('otherKey', 'otherValue');
            /* dataFormat为base64时配置发送数据的方式 */
            // data.base = file.base;
            // data.token = '387126b0-7b3e-4a2a-86ad-ae5c5edd0ae6TT';
            // data.otherKey = 'otherValue';
        },
        onChange: function (fileList) {
            /* input选中时触发 */
        },
        onRemove: function (removedFiles, files) {
            console.log('onRemove', removedFiles);
        },
        onSuccess: function (res) {
            console.log('onSuccess', res);
            var dir=res.data;
            $.get('/fs/list',{dir:dir},function(html){
                $('.fs-content').html(html);
            });

        },
        onError: function (err) {
            console.log('onError', err);
        },
    });
    $('#create-folder').on('keyup',function(e){
        if(e.keyCode!='13'){
            return;
        }
        var val=$(this).val();
        var currentdir=$('div[currentdir]').attr('currentdir');
        var dir='';
        if(currentdir=='/'){
            dir=currentdir+val;
        }else{
            dir=currentdir+"/"+val;
        }
        $.get('/dir/create.service',{dir:dir},function(data){
            var dir=currentdir;
            $.get('/fs/list',{dir:dir},function(html){
                $('.fs-content').html(html);
            });
        });
    });
    $.get('/fs/list',{dir:'/'},function(html){
        $('.fs-content').html(html);
    });
    $(".fs-content").undelegate('a[dir]','click');
    $(".fs-content").delegate('a[dir]','click',function(e){
        e.preventDefault();
        e.stopPropagation();
        var dir=$(this).attr('dir');
        $.get('/fs/list',{dir:dir},function(html){
            $('.fs-content').html(html);
        });
    });
    $(".fs-content").undelegate('a[parentDir]','click');
    $(".fs-content").delegate('a[parentDir]','click',function(e){
        e.preventDefault();
        e.stopPropagation();
        var dir=$(this).attr('parentDir');
        $.get('/fs/list',{dir:dir},function(html){
            $('.fs-content').html(html);
        });
    });
    $(".fs-content").undelegate('a[file]','click');
    $(".fs-content").delegate('a[file]','click',function(e){
        e.preventDefault();
        e.stopPropagation();
        var file=$(this).attr('href');
        window.location.href=file;
    });

    $('.fs-content').undelegate('li[type]','mouseenter mouseleave');
    $('.fs-content').delegate('li[type]','mouseenter mouseleave',function(){
        $(this).find('span.del').toggle();
    });
    $('.fs-content').undelegate('span.del','click');
    $('.fs-content').delegate('span.del','click',function(){
        var type=$(this).parents('li[type]').attr('type');
        var path = $(this).siblings('a').attr('path');
        $.get('/del/file/',{path:path,type:type},function(html){
            var currentdir=$('div[currentdir]').attr('currentdir');
            var dir=currentdir;
            $.get('/fs/list',{dir:dir},function(html){
                $('.fs-content').html(html);
            });
        });
    });
});