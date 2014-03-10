package us.codecraft.xsoup;

import org.jsoup.select.Elements;

import java.util.List;

/**
 * @author code4crafter@gmail.com
 */
public interface XElements extends List<XElement> {

    String get();

    List<String> list();

    Elements getElements();
}
